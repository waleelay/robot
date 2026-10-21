package gateway

import (
	"context"
	"sync/atomic"
	"testing"
	"time"

	"media-gateway/internal/config"
	"media-gateway/internal/media"
	"media-gateway/internal/model"
	"media-gateway/internal/protocol/rtsp"
	"media-gateway/internal/publisher"
	"media-gateway/internal/source"
)

type fakePublisher struct {
	stoppedSessionID string
	stopAllCalls     int
	startCalls       int
	restartCalls     int
}

func (p *fakePublisher) Start(context.Context, model.StartCommand, media.Input) (string, string, error) {
	p.startCalls++
	return "", "", nil
}

func (p *fakePublisher) Restart(context.Context, model.StartCommand, media.Input) (string, string, error) {
	p.restartCalls++
	return "", "", nil
}

func (p *fakePublisher) Stop(sessionID string) error {
	p.stoppedSessionID = sessionID
	return nil
}

func (p *fakePublisher) StopAll() error {
	p.stopAllCalls++
	return nil
}

type fakeProber struct {
	err error
}

type countingProber struct {
	calls atomic.Int32
}

func (p fakeProber) Check(context.Context, string) (media.StreamInfo, error) {
	return media.StreamInfo{CodecName: "h264"}, p.err
}

func (p *countingProber) Check(context.Context, string) (media.StreamInfo, error) {
	p.calls.Add(1)
	return media.StreamInfo{CodecName: "h264"}, nil
}

func TestStopOnlyUnbindsExactViewingSession(t *testing.T) {
	pub := &fakePublisher{}
	gateway := newTestGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, pub)

	gateway.stop(model.StopCommand{
		SessionID: "session-002", SourceType: "FIXED_CAMERA", SourceID: "camera-001", RoomName: "room-001",
	})

	if pub.stoppedSessionID != "session-002" {
		t.Fatalf("停止命令必须按会话解绑，实际会话ID=%q", pub.stoppedSessionID)
	}
}

func TestExplicitRestartUsesPublisherRestart(t *testing.T) {
	pub := &fakePublisher{}
	gateway := newTestGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, pub)
	gateway.subscriptionsReady.Store(true)
	gateway.beginStarting("session-001")

	gateway.startPublisher(context.Background(), model.StartCommand{
		SessionID: "session-001", SourceType: "FIXED_CAMERA", SourceID: "camera-001", DeviceID: "camera-001", SourceProtocol: "RTSP", Quality: "main",
		RoomName: "room-001", RTSPURL: "rtsp://camera/live",
	}, true)

	if pub.restartCalls != 1 {
		t.Fatalf("明确重启命令必须调用 Publisher.Restart，实际=%d", pub.restartCalls)
	}
}

func TestStoppedSessionRejectsLateStartAndRestart(t *testing.T) {
	pub := &fakePublisher{}
	gateway := newTestGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, pub)
	gateway.markSessionStopped("session-001")
	command := model.StartCommand{
		SessionID: "session-001", SourceType: "FIXED_CAMERA", SourceID: "camera-001", DeviceID: "camera-001", SourceProtocol: "RTSP", Quality: "main",
		RoomName: "room-001", RTSPURL: "rtsp://camera/live",
	}

	gateway.beginStarting(command.SessionID)
	gateway.startPublisher(context.Background(), command, false)
	gateway.beginStarting(command.SessionID)
	gateway.startPublisher(context.Background(), command, true)

	if pub.startCalls != 0 || pub.restartCalls != 0 {
		t.Fatalf("已终止会话不得被迟到命令复活，start=%d restart=%d", pub.startCalls, pub.restartCalls)
	}
}

func TestActiveSessionsAreRemovedAfterStopOrPublisherExit(t *testing.T) {
	gateway := newTestGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, &fakePublisher{})
	gateway.rememberSession("session-001")
	gateway.rememberSession("session-002")

	gateway.forgetSession("session-001")
	sessionIDs := gateway.activeSessionIDs()
	if len(sessionIDs) != 1 || sessionIDs[0] != "session-002" {
		t.Fatalf("停止或退出后只能保留仍在推流的会话，实际=%+v", sessionIDs)
	}
}

func TestStopAllPublishersClearsTrackedSessions(t *testing.T) {
	pub := &fakePublisher{}
	gateway := newTestGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, pub)
	gateway.rememberSession("session-001")
	gateway.rememberSession("session-002")

	gateway.stopAllPublishers("网关进程停止")

	if pub.stopAllCalls != 1 {
		t.Fatalf("退出时应停止全部推流进程，实际调用次数=%d", pub.stopAllCalls)
	}
	if sessionIDs := gateway.activeSessionIDs(); len(sessionIDs) != 0 {
		t.Fatalf("退出后不能保留活动会话，实际=%+v", sessionIDs)
	}
}

func TestMQTTConnectionLostStopsPublishersAndKeepsStatusesForReconnect(t *testing.T) {
	pub := &fakePublisher{}
	gateway := newTestGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, pub)
	gateway.dispatcher = newCommandDispatcher(context.Background())
	defer gateway.dispatcher.close()
	gateway.rememberSession("session-active")
	gateway.beginStarting("session-starting")
	gateway.beginStopping("session-stopping")
	gateway.commandStates["session-active"] = sessionCommandState{lastCommandID: "cmd-active"}
	gateway.commandStates["session-stopped"] = sessionCommandState{lastCommandID: "cmd-stop", stopped: true}

	gateway.handleConnectionLost()

	if pub.stopAllCalls != 1 {
		t.Fatalf("MQTT 断开必须停止全部推流，实际调用次数=%d", pub.stopAllCalls)
	}
	if sessionIDs := gateway.activeSessionIDs(); len(sessionIDs) != 0 {
		t.Fatalf("MQTT 断开后不能保留活动会话，实际=%+v", sessionIDs)
	}
	if status := gateway.pendingStatuses["session-active"]; status.status != "interrupted" {
		t.Fatalf("活动会话应在重连后补报中断，实际=%+v", status)
	}
	if status := gateway.pendingStatuses["session-starting"]; status.status != "interrupted" {
		t.Fatalf("启动中会话应在重连后补报中断，实际=%+v", status)
	}
	if status := gateway.pendingStatuses["session-stopping"]; status.status != "stopped" {
		t.Fatalf("停止中会话应在重连后补报停止，实际=%+v", status)
	}
	if commandID := gateway.commandStates["session-active"].lastCommandID; commandID != "" {
		t.Fatalf("未终止会话应允许重放原命令，实际命令ID=%q", commandID)
	}
	if state := gateway.commandStates["session-stopped"]; !state.stopped || state.lastCommandID != "cmd-stop" {
		t.Fatalf("已终止会话必须保留终止水位，实际=%+v", state)
	}
}

func TestCommandDispatcherSerializesSessionAndPrioritizesStop(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	dispatcher := newCommandDispatcher(ctx)
	defer dispatcher.close()
	started := make(chan struct{})
	order := make(chan string, 3)

	if err := dispatcher.submit(normalCommand, commandJob{
		ctx: ctx, sessionID: "session-001",
		run: func(jobCtx context.Context) {
			close(started)
			<-jobCtx.Done()
			order <- "start"
		},
	}); err != nil {
		t.Fatalf("提交启动命令失败：%v", err)
	}
	<-started
	if err := dispatcher.submit(normalCommand, commandJob{
		ctx: ctx, sessionID: "session-001", run: func(context.Context) { order <- "queued-start" },
	}); err != nil {
		t.Fatalf("提交排队启动命令失败：%v", err)
	}
	if err := dispatcher.submit(priorityCommand, commandJob{
		ctx: ctx, sessionID: "session-001", run: func(context.Context) { order <- "stop" },
	}); err != nil {
		t.Fatalf("提交停止命令失败：%v", err)
	}

	got := []string{<-order, <-order, <-order}
	if got[0] != "start" || got[1] != "stop" || got[2] != "queued-start" {
		t.Fatalf("同会话命令顺序错误，实际=%v", got)
	}
}

func newTestGateway(cfg config.Config, prober rtsp.Prober, pub publisher.Publisher) *Gateway {
	if cfg.ClientID == "" {
		cfg.ClientID = "test-media-client"
	}
	if pub == nil {
		pub = &fakePublisher{}
	}
	registry, _ := source.NewRegistry(nil, rtsp.NewAdapter(prober))
	now := time.Now()
	_ = registry.ApplyCatalog(source.CatalogSnapshot{Version: "1.0", GatewayID: cfg.GatewayID, CatalogVersion: 1, IssuedAt: now, Sources: []source.LeasedConfig{{Config: source.Config{SourceType: "FIXED_CAMERA", SourceID: "camera-001", DeviceID: "camera-001", Protocol: "RTSP", MainURL: "rtsp://camera/live"}, Enabled: true, ExpiresAt: now.Add(time.Minute)}}}, cfg.GatewayID, now)
	gw, err := NewMediaGateway(cfg, registry, pub)
	if err != nil {
		panic(err)
	}
	return gw
}

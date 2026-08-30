package fixedcamera

import (
	"context"
	"sync/atomic"
	"testing"
	"time"

	"fixed-camera-gateway/internal/config"
	"fixed-camera-gateway/internal/model"
	"fixed-camera-gateway/internal/rtsp"
)

type fakePublisher struct {
	stoppedSessionID string
	stopAllCalls     int
}

func (p *fakePublisher) Start(context.Context, model.StartCommand, string) (string, string, error) {
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

func TestLeaseCatalogReplacesSnapshotAndExpires(t *testing.T) {
	now := time.Date(2026, 8, 24, 0, 0, 0, 0, time.UTC)
	gateway := NewGateway(config.Config{
		GatewayID: "gateway-001",
	}, fakeProber{}, nil)

	err := gateway.applyCatalog(model.FixedCameraCatalogSnapshot{
		Version: "1.0", GatewayID: "gateway-001", CatalogVersion: 1, IssuedAt: now,
		Cameras: []model.FixedCameraCatalogRecord{{
			CameraID: "camera-001", Enabled: true, ProtocolType: "RTSP",
			SubStreamURL: "rtsp://camera/sub", ExpiresAt: now.Add(3 * time.Minute),
		}},
	}, now)
	if err != nil {
		t.Fatalf("应接受有效目录快照：%v", err)
	}
	if cameras := gateway.leasedCameras(now.Add(time.Minute)); len(cameras) != 1 || cameras[0].CameraID != "camera-001" {
		t.Fatalf("期望租约期内返回摄像头，实际=%+v", cameras)
	}
	if cameras := gateway.leasedCameras(now.Add(4 * time.Minute)); len(cameras) != 0 {
		t.Fatalf("期望过期租约被移除，实际=%+v", cameras)
	}
}

func TestLeaseCatalogRejectsOtherGateway(t *testing.T) {
	now := time.Now()
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, nil)
	err := gateway.applyCatalog(model.FixedCameraCatalogSnapshot{
		GatewayID: "gateway-002", IssuedAt: now,
	}, now)
	if err == nil {
		t.Fatal("期望拒绝其他网关的目录快照")
	}
}

func TestLeaseCatalogRejectsStaleVersionAtSameIssuedTime(t *testing.T) {
	now := time.Now()
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, nil)
	snapshot := model.FixedCameraCatalogSnapshot{
		GatewayID: "gateway-001", CatalogVersion: 2, IssuedAt: now,
	}
	if err := gateway.applyCatalog(snapshot, now); err != nil {
		t.Fatalf("应接受首份目录快照：%v", err)
	}
	snapshot.CatalogVersion = 1
	if err := gateway.applyCatalog(snapshot, now); err == nil {
		t.Fatal("期望拒绝同签发时间的旧版本目录快照")
	}
}

func (p fakeProber) Check(context.Context, string) (rtsp.StreamInfo, error) {
	return rtsp.StreamInfo{CodecName: "h264"}, p.err
}

func (p *countingProber) Check(context.Context, string) (rtsp.StreamInfo, error) {
	p.calls.Add(1)
	return rtsp.StreamInfo{CodecName: "h264"}, nil
}

func TestHealthProbeChecksSameRTSPOnlyOnce(t *testing.T) {
	prober := &countingProber{}
	gateway := NewGateway(config.Config{GatewayID: "gateway-001", HealthProbeWorkers: 4}, prober, nil)
	expiresAt := time.Now().Add(time.Minute)
	gateway.catalog["camera-001"] = leasedCamera{record: cameraRecord{
		CameraID: "camera-001", Enabled: true, ProtocolType: "RTSP", SubStreamURL: "rtsp://camera/live",
	}, expiresAt: expiresAt}
	gateway.catalog["camera-002"] = leasedCamera{record: cameraRecord{
		CameraID: "camera-002", Enabled: true, ProtocolType: "RTSP", SubStreamURL: "rtsp://camera/live",
	}, expiresAt: expiresAt}

	gateway.probeAllCameras(context.Background())

	if calls := prober.calls.Load(); calls != 1 {
		t.Fatalf("相同 RTSP 每轮只能探测一次，实际=%d", calls)
	}
}

func TestCameraHealthStatusUsesResolvedProbeResult(t *testing.T) {
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, nil)
	available := gateway.cameraHealthStatus(cameraRecord{CameraID: "camera-001"}, "AVAILABLE", "")
	if available.Health != "AVAILABLE" || available.ReasonCode != "" {
		t.Fatalf("期望摄像头可用，实际=%+v", available)
	}
	status := gateway.cameraHealthStatus(cameraRecord{CameraID: "camera-001"}, "UNAVAILABLE", "RTSP_PROBE_FAILED")
	if status.Health != "UNAVAILABLE" || status.ReasonCode != "RTSP_PROBE_FAILED" {
		t.Fatalf("期望探测失败，实际=%+v", status)
	}
}

func TestPublisherProcessExitIsReportedAsInterruptedForRecovery(t *testing.T) {
	if status := publisherEventStatus("PUBLISH_PROCESS_EXITED"); status != "interrupted" {
		t.Fatalf("推流进程意外退出应进入可恢复中断状态，实际=%s", status)
	}
	if status := publisherEventStatus("PUBLISH_TOKEN_EXPIRED"); status != "failed" {
		t.Fatalf("非断流原因不应被错误标记为可恢复中断，实际=%s", status)
	}
}

func TestStopOnlyUnbindsExactViewingSession(t *testing.T) {
	pub := &fakePublisher{}
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, pub)

	gateway.stop(model.StopCommand{
		SessionID: "session-002", SourceType: "FIXED_CAMERA", SourceID: "camera-001", RoomName: "room-001",
	})

	if pub.stoppedSessionID != "session-002" {
		t.Fatalf("停止命令必须按会话解绑，实际会话ID=%q", pub.stoppedSessionID)
	}
}

func TestActiveSessionsAreRemovedAfterStopOrPublisherExit(t *testing.T) {
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, &fakePublisher{})
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
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, pub)
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
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, pub)
	gateway.dispatcher = newCommandDispatcher(context.Background())
	defer gateway.dispatcher.close()
	gateway.rememberSession("session-active")
	gateway.beginStarting("session-starting")
	gateway.beginStopping("session-stopping")

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

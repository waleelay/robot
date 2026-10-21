package gateway

import (
	"context"
	"encoding/json"
	"sync"
	"testing"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
	"media-gateway/internal/config"
	"media-gateway/internal/media"
	"media-gateway/internal/model"
	"media-gateway/internal/protocol/rtsp"
	"media-gateway/internal/publisher"
	"media-gateway/internal/source"
)

type capturedMessage struct {
	topic string
	body  []byte
}

// captureClient 只替换测试使用的发布边界；不连接实际 MQTT 或媒体服务。
type captureClient struct {
	paho.Client
	mu       sync.Mutex
	messages []capturedMessage
	topics   []string
}

func (*captureClient) IsConnectionOpen() bool { return true }

func (c *captureClient) Subscribe(topic string, _ byte, _ paho.MessageHandler) paho.Token {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.topics = append(c.topics, topic)
	return completedToken{}
}
func (c *captureClient) Publish(topic string, qos byte, retained bool, body interface{}) paho.Token {
	if qos != 1 || retained {
		panic("状态 QoS/retained 不符合协议")
	}
	c.mu.Lock()
	c.messages = append(c.messages, capturedMessage{topic, append([]byte(nil), body.([]byte)...)})
	c.mu.Unlock()
	return completedToken{}
}

type completedToken struct{}

func (completedToken) Wait() bool                     { return true }
func (completedToken) WaitTimeout(time.Duration) bool { return true }
func (completedToken) Error() error                   { return nil }
func (completedToken) Done() <-chan struct{}          { ch := make(chan struct{}); close(ch); return ch }

type mediaProbe struct {
	codec string
	calls int
}

func (p *mediaProbe) Check(context.Context, string) (media.StreamInfo, error) {
	p.calls++
	return media.StreamInfo{CodecName: p.codec}, nil
}

type commandPublisher struct {
	fakePublisher
	command model.StartCommand
	input   media.Input
	url     string
}

func (p *commandPublisher) Start(_ context.Context, c model.StartCommand, input media.Input) (string, string, error) {
	p.startCalls++
	p.command = c
	p.input = input
	p.url = input.Address
	return "", "video.visible.main", nil
}

func mediaRegistry(t *testing.T, prober rtsp.Prober) *source.Registry {
	t.Helper()
	r, err := source.NewRegistry([]source.Config{
		{SourceType: "FIXED_CAMERA", SourceID: "same-id", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://ground/fixed"},
		{SourceType: "DRONE_CAMERA", SourceID: "same-id", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://ground/drone"},
	}, rtsp.NewAdapter(prober))
	if err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	if err := r.ApplyCatalog(source.CatalogSnapshot{Version: "1.0", GatewayID: "edge-1", CatalogVersion: 1, IssuedAt: now,
		Sources: []source.LeasedConfig{{Config: source.Config{SourceType: "FIXED_CAMERA", SourceID: "same-id", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://ground/fixed"}, Enabled: true, ExpiresAt: now.Add(time.Minute)}}}, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	return r
}

func mediaCommand(typ, session string) model.StartCommand {
	return model.StartCommand{
		CommandID: "cmd-" + session, SessionID: session, SourceType: typ, SourceID: "same-id", DeviceID: "camera01", SourceProtocol: "RTSP",
		Channel: "visible", Quality: "main", RoomName: "media." + typ + ".same-id.camera01.visible.main", LiveKitURL: "wss://livekit.example",
		PublisherToken: "token", ExpiresAt: time.Now().Add(time.Minute),
	}
}

func TestMediaGatewayUsesVerifiedCodecAndUnifiedStatus(t *testing.T) {
	probe := &mediaProbe{codec: "hevc"}
	pub := &commandPublisher{}
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1"}, mediaRegistry(t, probe), pub)
	if err != nil {
		t.Fatal(err)
	}
	mqtt := &captureClient{}
	gw.mqtt = mqtt
	cmd := mediaCommand("DRONE_CAMERA", "session-1")
	if err := gw.validateMediaStart(cmd); err != nil {
		t.Fatal(err)
	}
	gw.beginStarting(cmd.SessionID)
	gw.start(context.Background(), cmd)
	if pub.startCalls != 1 || pub.input.Info.CodecName != "hevc" || pub.url != "rtsp://ground/drone" {
		t.Fatalf("启动未使用已验证源：%+v", pub)
	}
	if len(mqtt.messages) != 2 {
		t.Fatalf("缺少启动状态：%d", len(mqtt.messages))
	}
	for _, m := range mqtt.messages {
		var status model.StatusMessage
		if err := json.Unmarshal(m.body, &status); err != nil {
			t.Fatal(err)
		}
		if m.topic != "gateway/media/edge-1/video/status" || status.SourceType != "DRONE_CAMERA" || status.SourceID != "same-id" || status.TrackSid != "" {
			t.Fatalf("统一状态路由或轨道事实错误：%s %+v", m.topic, status)
		}
	}
	gw.stop(model.StopCommand{SessionID: cmd.SessionID})
	if pub.stoppedSessionID != cmd.SessionID || len(gw.activeSessionIDs()) != 0 {
		t.Fatal("停止没有释放会话")
	}
}

func TestUnifiedGatewayRestoresOnlyItsOwnTopics(t *testing.T) {
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1"}, mediaRegistry(t, &mediaProbe{codec: "h264"}), &fakePublisher{})
	if err != nil {
		t.Fatal(err)
	}
	if gw.Ready() {
		t.Fatal("尚未订阅不得就绪")
	}
	mqtt := &captureClient{}
	gw.mqtt = mqtt
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	gw.restoreSubscriptions(ctx, gw.topicPrefix()+"/video/start", gw.topicPrefix()+"/video/stop", gw.topicPrefix()+"/video/restart", gw.topicPrefix()+"/catalog/sync")
	if !gw.Ready() || len(mqtt.topics) != 4 {
		t.Fatalf("订阅恢复不完整：%v", mqtt.topics)
	}
	for _, topic := range mqtt.topics {
		if len(topic) < len("gateway/media/edge-1/") || topic[:len("gateway/media/edge-1/")] != "gateway/media/edge-1/" {
			t.Fatalf("不得订阅固定摄像头旧主题：%s", topic)
		}
	}
	gw.handleConnectionLost()
	if gw.Ready() {
		t.Fatal("MQTT 断开后不得继续就绪")
	}
}

// testMessage 允许直接验证 MQTT 入站规则，不创建真实网络连接。
type testMessage struct {
	body     []byte
	retained bool
}

func (m testMessage) Duplicate() bool   { return false }
func (m testMessage) Qos() byte         { return 1 }
func (m testMessage) Retained() bool    { return m.retained }
func (m testMessage) Topic() string     { return "gateway/media/edge-1/video/start" }
func (m testMessage) MessageID() uint16 { return 1 }
func (m testMessage) Payload() []byte   { return m.body }
func (m testMessage) Ack()              {}

func TestInvalidAndRetainedCommandsCannotMutateExistingSession(t *testing.T) {
	pub := &fakePublisher{}
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1"}, mediaRegistry(t, &mediaProbe{codec: "h264"}), pub)
	if err != nil {
		t.Fatal(err)
	}
	cmd := mediaCommand("DRONE_CAMERA", "session-1")
	if err := gw.validateMediaStart(cmd); err != nil {
		t.Fatal(err)
	}
	gw.rememberSession(cmd.SessionID)
	mqtt := &captureClient{}
	gw.mqtt = mqtt
	body, err := json.Marshal(cmd)
	if err != nil {
		t.Fatal(err)
	}
	gw.handleStart(context.Background(), normalCommand)(nil, testMessage{body: body, retained: true})
	cmd.SourceType = "FIXED_CAMERA"
	body, err = json.Marshal(cmd)
	if err != nil {
		t.Fatal(err)
	}
	gw.handleStart(context.Background(), normalCommand)(nil, testMessage{body: body})
	stop := model.StopCommand{SessionID: cmd.SessionID, SourceType: "FIXED_CAMERA", SourceID: cmd.SourceID, DeviceID: cmd.DeviceID}
	body, err = json.Marshal(stop)
	if err != nil {
		t.Fatal(err)
	}
	gw.handleStop(context.Background())(nil, testMessage{body: body})
	if pub.startCalls != 0 || pub.stoppedSessionID != "" || gw.sessionStopped(cmd.SessionID) || len(mqtt.messages) != 0 || len(gw.activeSessionIDs()) != 1 {
		t.Fatal("未验证身份或 retained 命令不能启动、停止或污染既有会话状态")
	}
}

func TestMediaGatewayRejectsInjectedAddressCrossSourceAndExpiredCommand(t *testing.T) {
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1"}, mediaRegistry(t, &mediaProbe{codec: "h264"}), &fakePublisher{})
	if err != nil {
		t.Fatal(err)
	}
	cmd := mediaCommand("DRONE_CAMERA", "session-1")
	if err := gw.validateMediaStart(cmd); err != nil {
		t.Fatal(err)
	}
	for _, modify := range []func(*model.StartCommand){
		func(c *model.StartCommand) { c.RTSPURL = "rtsp://other/secret" },
		func(c *model.StartCommand) { c.SourceType = "FIXED_CAMERA" },
		func(c *model.StartCommand) { c.SourceProtocol = "RTMP" },
		func(c *model.StartCommand) { c.ExpiresAt = time.Now().Add(-time.Second) },
	} {
		bad := cmd
		modify(&bad)
		if err := gw.validateMediaStart(bad); err == nil {
			t.Fatal("应拒绝无效命令")
		}
	}
	if err := gw.bindMediaSession(model.StopCommand{SessionID: cmd.SessionID, SourceType: "FIXED_CAMERA", SourceID: "same-id", DeviceID: "camera01"}); err == nil {
		t.Fatal("跨源停止不得影响无人机会话")
	}
}

func TestUnifiedGatewayMixedSourcesSharePublisherCoreAndStopIndependently(t *testing.T) {
	pub := publisher.NewProcessPublisher(config.Config{PublisherCmd: "/bin/sleep 30", PublisherStopTimeout: time.Second})
	t.Cleanup(func() { _ = pub.StopAll() })
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1"}, mediaRegistry(t, &mediaProbe{codec: "h264"}), pub)
	if err != nil {
		t.Fatal(err)
	}
	gw.mqtt = &captureClient{}
	for _, cmd := range []model.StartCommand{mediaCommand("DRONE_CAMERA", "drone-viewer-1"), mediaCommand("DRONE_CAMERA", "drone-viewer-2"), mediaCommand("FIXED_CAMERA", "fixed-viewer-1")} {
		if err := gw.validateMediaStart(cmd); err != nil {
			t.Fatal(err)
		}
		gw.beginStarting(cmd.SessionID)
		gw.start(context.Background(), cmd)
	}
	if s := pub.Snapshot(); s.ActivePublishers != 2 || s.ActiveSessions != 3 {
		t.Fatalf("混合源隔离或观看复用失败：%+v", s)
	}
	gw.stop(model.StopCommand{SessionID: "drone-viewer-1"})
	if s := pub.Snapshot(); s.ActivePublishers != 2 || s.ActiveSessions != 2 {
		t.Fatalf("首个无人机 viewer 不得停止共享进程：%+v", s)
	}
	gw.stop(model.StopCommand{SessionID: "fixed-viewer-1"})
	if s := pub.Snapshot(); s.ActivePublishers != 1 || s.ActiveSessions != 1 {
		t.Fatalf("固定摄像头停止不得影响无人机：%+v", s)
	}
	gw.markSessionStopped("drone-viewer-2")
	gw.stop(model.StopCommand{SessionID: "drone-viewer-2"})
	cmd := mediaCommand("DRONE_CAMERA", "drone-viewer-2")
	gw.beginStarting(cmd.SessionID)
	gw.startPublisher(context.Background(), cmd, true)
	if s := pub.Snapshot(); s.ActivePublishers != 0 || s.ActiveSessions != 0 {
		t.Fatalf("停止后迟到重启不得复活进程：%+v", s)
	}
}

func TestRevokedLeaseStopsOnlyFixedSourceAndBlocksLateRestart(t *testing.T) {
	registry := mediaRegistry(t, &mediaProbe{codec: "h264"})
	pub := publisher.NewProcessPublisher(config.Config{PublisherCmd: "/bin/sleep 30", PublisherStopTimeout: time.Second})
	t.Cleanup(func() { _ = pub.StopAll() })
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1"}, registry, pub)
	if err != nil {
		t.Fatal(err)
	}
	gw.mqtt = &captureClient{}
	for _, cmd := range []model.StartCommand{mediaCommand("FIXED_CAMERA", "fixed-viewer"), mediaCommand("DRONE_CAMERA", "drone-viewer")} {
		if err := gw.validateMediaStart(cmd); err != nil {
			t.Fatal(err)
		}
		gw.beginStarting(cmd.SessionID)
		gw.start(context.Background(), cmd)
	}
	if err := registry.ApplyCatalog(source.CatalogSnapshot{Version: "1.0", GatewayID: "edge-1", CatalogVersion: 2, IssuedAt: time.Now()}, "edge-1", time.Now()); err != nil {
		t.Fatal(err)
	}
	gw.sweepRevokedSources()
	if s := pub.Snapshot(); s.ActivePublishers != 1 || s.ActiveSessions != 1 {
		t.Fatalf("撤权不得影响另一源：%+v", s)
	}
	cmd := mediaCommand("FIXED_CAMERA", "fixed-viewer")
	gw.beginStarting(cmd.SessionID)
	gw.startPublisher(context.Background(), cmd, true)
	if s := pub.Snapshot(); s.ActivePublishers != 1 || s.ActiveSessions != 1 {
		t.Fatalf("撤权后不得复活固定源：%+v", s)
	}
}

func TestStartCanWaitForCatalogWithoutBypassingLease(t *testing.T) {
	registry, err := source.NewRegistry(nil, rtsp.NewAdapter(&mediaProbe{codec: "h264"}))
	if err != nil {
		t.Fatal(err)
	}
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1", ProbeTimeout: time.Second}, registry, &fakePublisher{})
	if err != nil {
		t.Fatal(err)
	}
	cmd := mediaCommand("FIXED_CAMERA", "waiting-session")
	if err := gw.validateMediaStart(cmd); err != nil {
		t.Fatal(err)
	}
	result := make(chan error, 1)
	go func() { _, err := gw.resolveMediaInput(context.Background(), cmd); result <- err }()
	now := time.Now()
	if err := registry.ApplyCatalog(source.CatalogSnapshot{Version: "1.0", GatewayID: "edge-1", CatalogVersion: 1, IssuedAt: now, Sources: []source.LeasedConfig{{Config: source.Config{SourceType: "FIXED_CAMERA", SourceID: "same-id", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://authorized/main"}, Enabled: true, ExpiresAt: now.Add(time.Minute)}}}, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	select {
	case err := <-result:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("目录到达后启动未恢复")
	}
}

func TestUnifiedHealthProbeMergesSameInputAcrossSources(t *testing.T) {
	probe := &countingProber{}
	r, err := source.NewRegistry([]source.Config{{SourceType: "DRONE_CAMERA", SourceID: "drone-001", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://shared/live"}}, rtsp.NewAdapter(probe))
	if err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	if err := r.ApplyCatalog(source.CatalogSnapshot{Version: "1.0", GatewayID: "edge-1", CatalogVersion: 1, IssuedAt: now, Sources: []source.LeasedConfig{
		{Config: source.Config{SourceType: "FIXED_CAMERA", SourceID: "fixed-001", DeviceID: "fixed-001", Protocol: "RTSP", MainURL: "rtsp://shared/live"}, Enabled: true, ExpiresAt: now.Add(time.Minute)},
		{Config: source.Config{SourceType: "FIXED_CAMERA", SourceID: "fixed-002", DeviceID: "fixed-002", Protocol: "RTSP", MainURL: "rtsp://shared/live"}, Enabled: true, ExpiresAt: now.Add(time.Minute)},
	}}, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1", HealthProbeWorkers: 4}, r, &fakePublisher{})
	if err != nil {
		t.Fatal(err)
	}
	mqtt := &captureClient{}
	gw.mqtt = mqtt
	gw.probeAllSources(context.Background())
	if probe.calls.Load() != 1 || len(mqtt.messages) != 3 {
		t.Fatalf("共享输入只探测一次并分别上报三路健康：次数=%d 状态数=%d", probe.calls.Load(), len(mqtt.messages))
	}
}

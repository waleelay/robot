package fixedcamera

import (
	"context"
	"errors"
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

type sequenceProber struct {
	errors []error
	index  int
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

func (p *sequenceProber) Check(context.Context, string) (rtsp.StreamInfo, error) {
	if p.index >= len(p.errors) {
		return rtsp.StreamInfo{CodecName: "h264"}, nil
	}
	err := p.errors[p.index]
	p.index++
	return rtsp.StreamInfo{CodecName: "h264"}, err
}

func TestCameraHealthSeparatesConfigurationAndRTSPAvailability(t *testing.T) {
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{}, nil)

	available := gateway.cameraHealth(context.Background(), cameraRecord{
		CameraID: "camera-001", Enabled: true, ProtocolType: "RTSP", SubStreamURL: "rtsp://example.invalid/stream",
	})
	if available.Health != "AVAILABLE" || available.ReasonCode != "" {
		t.Fatalf("期望摄像头可用，实际=%+v", available)
	}

	disabled := gateway.cameraHealth(context.Background(), cameraRecord{
		CameraID: "camera-002", Enabled: false, SubStreamURL: "rtsp://example.invalid/stream",
	})
	if disabled.Health != "UNKNOWN" || disabled.ReasonCode != "CONFIG_DISABLED" {
		t.Fatalf("期望停用摄像头健康未知，实际=%+v", disabled)
	}
}

func TestCameraHealthReportsProbeFailureWithoutLeakingURL(t *testing.T) {
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, fakeProber{err: errors.New("探测失败")}, nil)

	status := gateway.cameraHealth(context.Background(), cameraRecord{
		CameraID: "camera-001", Enabled: true, ProtocolType: "RTSP", MainStreamURL: "rtsp://secret:password@example.invalid/stream",
	})

	if status.Health != "UNAVAILABLE" || status.ReasonCode != "RTSP_PROBE_FAILED" {
		t.Fatalf("期望探测失败，实际=%+v", status)
	}
}

func TestCameraHealthRecoversAfterRTSPBecomesAvailable(t *testing.T) {
	prober := &sequenceProber{errors: []error{errors.New("断流"), nil}}
	gateway := NewGateway(config.Config{GatewayID: "gateway-001"}, prober, nil)
	camera := cameraRecord{
		CameraID: "camera-001", Enabled: true, ProtocolType: "RTSP", SubStreamURL: "rtsp://example.invalid/stream",
	}

	unavailable := gateway.cameraHealth(context.Background(), camera)
	available := gateway.cameraHealth(context.Background(), camera)

	if unavailable.Health != "UNAVAILABLE" || unavailable.ReasonCode != "RTSP_PROBE_FAILED" {
		t.Fatalf("断流后应发布不可用状态，实际=%+v", unavailable)
	}
	if available.Health != "AVAILABLE" || available.ReasonCode != "" {
		t.Fatalf("恢复后应重新发布可用状态，实际=%+v", available)
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

func TestRecoveryProbeRetriesAreBounded(t *testing.T) {
	gateway := NewGateway(config.Config{}, fakeProber{}, nil)
	gateway.recoveryAttempts["vs-1"] = 0
	for attempt := 0; attempt < maxAutomaticRecoveryAttempts; attempt++ {
		if status := gateway.recoveryProbeStatus("vs-1"); status != "interrupted" {
			t.Fatalf("第 %d 次恢复探测失败应保持中断，实际=%s", attempt+1, status)
		}
	}
	if status := gateway.recoveryProbeStatus("vs-1"); status != "failed" {
		t.Fatalf("超过恢复上限应失败收口，实际=%s", status)
	}
	if status := gateway.recoveryProbeStatus("vs-new"); status != "failed" {
		t.Fatalf("首次启动探测失败应直接失败，实际=%s", status)
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

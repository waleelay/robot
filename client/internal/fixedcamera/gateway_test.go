package fixedcamera

import (
	"context"
	"errors"
	"testing"
	"time"

	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
	"robot-media-client/internal/rtsp"
)

type fakeProber struct {
	err error
}

func TestLeaseCatalogReplacesSnapshotAndExpires(t *testing.T) {
	now := time.Date(2026, 8, 24, 0, 0, 0, 0, time.UTC)
	gateway := NewGateway(config.Config{
		FixedCameraGatewayID: "gateway-001", FixedCameraCatalogMode: "lease",
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
	gateway := NewGateway(config.Config{FixedCameraGatewayID: "gateway-001"}, fakeProber{}, nil)
	err := gateway.applyCatalog(model.FixedCameraCatalogSnapshot{
		GatewayID: "gateway-002", IssuedAt: now,
	}, now)
	if err == nil {
		t.Fatal("期望拒绝其他网关的目录快照")
	}
}

func TestLeaseCatalogRejectsStaleVersionAtSameIssuedTime(t *testing.T) {
	now := time.Now()
	gateway := NewGateway(config.Config{FixedCameraGatewayID: "gateway-001"}, fakeProber{}, nil)
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

func TestCameraHealthSeparatesConfigurationAndRTSPAvailability(t *testing.T) {
	gateway := NewGateway(config.Config{FixedCameraGatewayID: "gateway-001"}, fakeProber{}, nil)

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
	gateway := NewGateway(config.Config{FixedCameraGatewayID: "gateway-001"}, fakeProber{err: errors.New("探测失败")}, nil)

	status := gateway.cameraHealth(context.Background(), cameraRecord{
		CameraID: "camera-001", Enabled: true, ProtocolType: "RTSP", MainStreamURL: "rtsp://secret:password@example.invalid/stream",
	})

	if status.Health != "UNAVAILABLE" || status.ReasonCode != "RTSP_PROBE_FAILED" {
		t.Fatalf("期望探测失败，实际=%+v", status)
	}
}

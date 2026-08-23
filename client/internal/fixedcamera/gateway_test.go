package fixedcamera

import (
	"context"
	"errors"
	"testing"

	"robot-media-client/internal/config"
	"robot-media-client/internal/rtsp"
)

type fakeProber struct {
	err error
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

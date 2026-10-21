package source

import (
	"context"
	"testing"
	"time"

	"media-gateway/internal/media"
)

// testAdapter 仅用于证明源目录不依赖 RTSP，不表示生产新增输入协议。
type testAdapter struct{ address string }

func (*testAdapter) Protocol() string             { return "TEST" }
func (*testAdapter) ValidateAddress(string) error { return nil }
func (a *testAdapter) Probe(_ context.Context, address string) (media.StreamInfo, error) {
	a.address = address
	return media.StreamInfo{CodecName: "h264", Width: 1920, Height: 1080}, nil
}

func TestCatalogLookupAndProbeDoNotAssumeRTSP(t *testing.T) {
	adapter := &testAdapter{}
	r, err := NewRegistry(nil, adapter)
	if err != nil {
		t.Fatal(err)
	}
	r.RequireAuthorizedCatalog()
	now := time.Now()
	snapshot := CatalogSnapshot{Version: "1.0", GatewayID: "edge-1", CatalogVersion: 1, IssuedAt: now,
		Sources: []LeasedConfig{{Config: Config{SourceType: "FIXED_CAMERA", SourceID: "camera01", DeviceID: "camera01", Protocol: "TEST", MainURL: "test:stream"}, Enabled: true, ExpiresAt: now.Add(time.Minute)}}}
	if err := r.ApplyCatalog(snapshot, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	cfg, err := r.Lookup("FIXED_CAMERA", "camera01", "camera01")
	if err != nil || cfg.Protocol != "TEST" {
		t.Fatalf("目录查询不能假定 RTSP：%+v %v", cfg, err)
	}
	input, err := r.Resolve(cfg.SourceType, cfg.SourceID, cfg.DeviceID, cfg.Protocol, "main")
	if err != nil {
		t.Fatal(err)
	}
	input, err = r.Probe(context.Background(), input)
	if err != nil || adapter.address != "test:stream" || input.Info.Width != 1920 || input.Protocol != "TEST" {
		t.Fatalf("通用输入未交给注册适配器：%+v %v", input, err)
	}
	snapshot.CatalogVersion++
	snapshot.Sources = nil
	if err := r.ApplyCatalog(snapshot, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	if _, err := r.Lookup(cfg.SourceType, cfg.SourceID, cfg.DeviceID); media.Reason(err) != "SOURCE_LEASE_MISSING" {
		t.Fatalf("撤权后不可继续读取通用源：%v", err)
	}
}

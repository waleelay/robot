package source

import (
	"testing"
	"time"

	"media-gateway/internal/media"
	"media-gateway/internal/protocol/rtsp"
)

func leasedSnapshot(now time.Time) CatalogSnapshot {
	return CatalogSnapshot{Version: "1.0", GatewayID: "edge-1", CatalogVersion: 1, IssuedAt: now,
		Sources: []LeasedConfig{{Config: Config{SourceType: "FIXED_CAMERA", SourceID: "camera01", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://authorized/main"}, Enabled: true, ExpiresAt: now.Add(time.Minute)}}}
}

func TestAuthorizedCatalogRevocationCannotFallBackToLocalFixedSource(t *testing.T) {
	r, err := NewRegistry([]Config{{SourceType: "FIXED_CAMERA", SourceID: "camera01", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://must-not-fallback/main"}}, rtsp.NewAdapter(probeStub{codec: "h264"}))
	if err != nil {
		t.Fatal(err)
	}
	r.RequireAuthorizedCatalog()
	if _, err := r.Resolve("FIXED_CAMERA", "camera01", "camera01", "RTSP", "main"); media.Reason(err) != "SOURCE_LEASE_MISSING" {
		t.Fatal("没有租约不得使用本地固定源")
	}
	now := time.Now()
	snapshot := leasedSnapshot(now)
	if err := r.ApplyCatalog(snapshot, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	input, err := r.Resolve("FIXED_CAMERA", "camera01", "camera01", "RTSP", "main")
	if err != nil || input.Address != "rtsp://authorized/main" {
		t.Fatalf("授权目录未生效：%+v %v", input, err)
	}
	snapshot.Sources = nil
	snapshot.CatalogVersion = 2
	if err := r.ApplyCatalog(snapshot, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	if _, err := r.Resolve("FIXED_CAMERA", "camera01", "camera01", "RTSP", "main"); media.Reason(err) != "SOURCE_LEASE_MISSING" {
		t.Fatal("撤权后不得回退本地源")
	}
	if len(r.Sources()) != 0 {
		t.Fatal("撤权后不得继续健康探测")
	}
}

func TestCatalogRejectsOtherGatewayStaleAndLongLeaseAtomically(t *testing.T) {
	r, err := NewRegistry(nil, rtsp.NewAdapter(probeStub{codec: "h264"}))
	if err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	snapshot := leasedSnapshot(now)
	if err := r.ApplyCatalog(snapshot, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	for _, test := range []struct {
		name   string
		change func(*CatalogSnapshot)
	}{
		{"其他网关", func(s *CatalogSnapshot) { s.GatewayID = "edge-2" }},
		{"重复版本", func(s *CatalogSnapshot) {}},
		{"旧签发时间", func(s *CatalogSnapshot) { s.IssuedAt = now.Add(-time.Second); s.CatalogVersion = 2 }},
		{"超长租约", func(s *CatalogSnapshot) { s.CatalogVersion = 2; s.Sources[0].ExpiresAt = now.Add(301 * time.Second) }},
	} {
		t.Run(test.name, func(t *testing.T) {
			bad := leasedSnapshot(now)
			test.change(&bad)
			if err := r.ApplyCatalog(bad, "edge-1", now); err == nil {
				t.Fatal("应拒绝无效快照")
			}
			if len(r.Sources()) != 1 {
				t.Fatal("拒绝快照不得部分撤销已有目录")
			}
		})
	}
}

func TestExpiredLeaseIsRemovedFromResolveAndProbeView(t *testing.T) {
	r, err := NewRegistry(nil, rtsp.NewAdapter(probeStub{codec: "h264"}))
	if err != nil {
		t.Fatal(err)
	}
	r.RequireAuthorizedCatalog()
	past := time.Now().Add(-2 * time.Minute)
	if err := r.ApplyCatalog(leasedSnapshot(past), "edge-1", past); err != nil {
		t.Fatal(err)
	}
	if _, err := r.Resolve("FIXED_CAMERA", "camera01", "camera01", "RTSP", "main"); media.Reason(err) != "SOURCE_LEASE_MISSING" {
		t.Fatal("实际时间已经过期的源不得使用")
	}
	if len(r.Sources()) != 0 {
		t.Fatal("过期源不得继续探测")
	}
}

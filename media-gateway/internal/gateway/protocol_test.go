package gateway

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"media-gateway/internal/config"
	"media-gateway/internal/media"
	"media-gateway/internal/model"
	"media-gateway/internal/source"
)

// testInputAdapter 只替换测试边界，生产仍只注册 RTSP。
type testInputAdapter struct{}

func (testInputAdapter) Protocol() string             { return "TEST" }
func (testInputAdapter) ValidateAddress(string) error { return nil }
func (testInputAdapter) Probe(context.Context, string) (media.StreamInfo, error) {
	return media.StreamInfo{CodecName: "h264"}, nil
}

func TestLeaseSweepUsesConfiguredProtocolAndRevokesExactSession(t *testing.T) {
	registry, err := source.NewRegistry(nil, testInputAdapter{})
	if err != nil {
		t.Fatal(err)
	}
	pub := &fakePublisher{}
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1"}, registry, pub)
	if err != nil {
		t.Fatal(err)
	}
	mqtt := &captureClient{}
	gw.mqtt = mqtt
	now := time.Now()
	snapshot := source.CatalogSnapshot{Version: "1.0", GatewayID: "edge-1", CatalogVersion: 1, IssuedAt: now,
		Sources: []source.LeasedConfig{{Config: source.Config{SourceType: "FIXED_CAMERA", SourceID: "camera01", DeviceID: "camera01", Protocol: "TEST", MainURL: "test:stream"}, Enabled: true, ExpiresAt: now.Add(time.Minute)}}}
	if err := registry.ApplyCatalog(snapshot, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	identity := model.StopCommand{SessionID: "session-test", SourceType: "FIXED_CAMERA", SourceID: "camera01", DeviceID: "camera01"}
	if err := gw.bindMediaSession(identity); err != nil {
		t.Fatal(err)
	}
	gw.leaseBoundSessions[identity.SessionID] = true
	gw.rememberSession(identity.SessionID)
	gw.sweepRevokedSources()
	if pub.stoppedSessionID != "" || gw.sessionStopped(identity.SessionID) {
		t.Fatal("有效的非 RTSP 配置不能被写死的协议检查误判为撤权")
	}
	snapshot.CatalogVersion++
	snapshot.Sources = nil
	if err := registry.ApplyCatalog(snapshot, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	gw.sweepRevokedSources()
	if pub.stoppedSessionID != identity.SessionID || !gw.sessionStopped(identity.SessionID) || len(gw.activeSessionIDs()) != 0 {
		t.Fatal("撤权必须停止对应会话并建立终止水位")
	}
	var status model.StatusMessage
	if len(mqtt.messages) != 1 {
		t.Fatalf("缺少撤权状态：%d", len(mqtt.messages))
	}
	if err := json.Unmarshal(mqtt.messages[0].body, &status); err != nil {
		t.Fatal(err)
	}
	if status.ErrorCode != "SOURCE_LEASE_MISSING" || status.SourceID != identity.SourceID || status.Status != "stopped" {
		t.Fatalf("撤权状态未保留通用身份：%+v", status)
	}
}

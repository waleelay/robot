package gateway

import (
	"context"
	"encoding/json"
	"os"
	"testing"
	"time"

	"media-gateway/internal/config"
	"media-gateway/internal/model"
	"media-gateway/internal/protocol/rtsp"
	"media-gateway/internal/source"
)

// TestControlContractFixture 消费真实 Java MQTT 序列化产物，验证目录、时间、选流、启停和状态路由。
func TestControlContractFixture(t *testing.T) {
	path := os.Getenv("MEDIA_GATEWAY_CONTRACT_FIXTURE")
	if path == "" {
		t.Skip("未提供 Java 生成的跨语言契约样例")
	}
	body, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var fixture struct {
		Catalog source.CatalogSnapshot `json:"catalog"`
		Start   model.StartCommand     `json:"start"`
		Stop    model.StopCommand      `json:"stop"`
	}
	if err := json.Unmarshal(body, &fixture); err != nil {
		t.Fatal(err)
	}
	registry, err := source.NewRegistry(nil, rtsp.NewAdapter(&mediaProbe{codec: "h264"}))
	if err != nil {
		t.Fatal(err)
	}
	pub := &commandPublisher{}
	gw, err := NewMediaGateway(config.Config{GatewayID: "contract-edge", ClientID: "contract-client"}, registry, pub)
	if err != nil {
		t.Fatal(err)
	}
	gw.mqtt = &captureClient{}
	if err := registry.ApplyCatalog(fixture.Catalog, "contract-edge", time.Now()); err != nil {
		t.Fatal(err)
	}
	if err := gw.validateMediaStart(fixture.Start); err != nil {
		t.Fatal(err)
	}
	gw.beginStarting(fixture.Start.SessionID)
	gw.start(context.Background(), fixture.Start)
	if pub.startCalls != 1 || pub.url != "rtsp://camera.example/main" {
		t.Fatalf("Java 命令未正确解析授权源：%+v", pub)
	}
	if err := gw.bindMediaSession(fixture.Stop); err != nil {
		t.Fatal(err)
	}
	gw.markSessionStopped(fixture.Stop.SessionID)
	gw.stop(fixture.Stop)
	if pub.stoppedSessionID != fixture.Start.SessionID || len(gw.activeSessionIDs()) != 0 {
		t.Fatal("跨语言 stop 未精确释放")
	}
}

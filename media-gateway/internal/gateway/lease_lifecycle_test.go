package gateway

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
	"media-gateway/internal/config"
	"media-gateway/internal/media"
	"media-gateway/internal/model"
	"media-gateway/internal/protocol/rtsp"
	"media-gateway/internal/source"
)

// blockingLeaseProbe 将探测窗口固定在租约截止时间前后，不连接真实摄像头。
type blockingLeaseProbe struct{ entered, release chan struct{} }

func (p *blockingLeaseProbe) Check(ctx context.Context, _ string) (media.StreamInfo, error) {
	close(p.entered)
	select {
	case <-p.release:
		return media.StreamInfo{CodecName: "h264"}, nil
	case <-ctx.Done():
		return media.StreamInfo{}, ctx.Err()
	}
}

func leaseLifecycleGateway(t *testing.T, prober rtsp.Prober, expiry time.Time) (*Gateway, *fakePublisher, source.CatalogSnapshot) {
	t.Helper()
	registry, err := source.NewRegistry(nil, rtsp.NewAdapter(prober))
	if err != nil {
		t.Fatal(err)
	}
	pub := &fakePublisher{}
	gw, err := NewMediaGateway(config.Config{GatewayID: "edge-1", ClientID: "media-edge-1", ProbeTimeout: time.Second}, registry, pub)
	if err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	snapshot := source.CatalogSnapshot{Version: "1.0", GatewayID: "edge-1", CatalogVersion: 1, IssuedAt: now,
		Sources: []source.LeasedConfig{{Config: source.Config{SourceType: "FIXED_CAMERA", SourceID: "same-id", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://camera.example/main"}, Enabled: true, ExpiresAt: expiry}}}
	if err := registry.ApplyCatalog(snapshot, "edge-1", now); err != nil {
		t.Fatal(err)
	}
	return gw, pub, snapshot
}

func lastCapturedLeaseStatus(t *testing.T, client *captureClient) model.StatusMessage {
	t.Helper()
	client.mu.Lock()
	defer client.mu.Unlock()
	if len(client.messages) == 0 {
		t.Fatal("没有媒体状态消息")
	}
	var status model.StatusMessage
	if err := json.Unmarshal(client.messages[len(client.messages)-1].body, &status); err != nil {
		t.Fatal(err)
	}
	return status
}

func TestLeaseExpiresDuringProbeRejectsStartAndRestart(t *testing.T) {
	for _, restart := range []bool{false, true} {
		name := "start"
		if restart {
			name = "restart"
		}
		t.Run(name, func(t *testing.T) {
			probe := &blockingLeaseProbe{entered: make(chan struct{}), release: make(chan struct{})}
			expiry := time.Now().Add(300 * time.Millisecond)
			gw, pub, snapshot := leaseLifecycleGateway(t, probe, expiry)
			client := &captureClient{}
			gw.mqtt = client
			command := mediaCommand("FIXED_CAMERA", "expiring-session")
			if err := gw.validateMediaStart(command); err != nil {
				t.Fatal(err)
			}
			gw.beginStarting(command.SessionID)
			ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
			defer cancel()
			done := make(chan struct{})
			go func() { defer close(done); gw.startPublisher(ctx, command, restart) }()
			select {
			case <-probe.entered:
			case <-ctx.Done():
				t.Fatal("探测未启动")
			}
			// 先确保本地租约自然过期，再允许探测成功，覆盖审查时发现的授权窗口。
			time.Sleep(time.Until(expiry) + 20*time.Millisecond)
			close(probe.release)
			select {
			case <-done:
			case <-ctx.Done():
				t.Fatal("发布检查未结束")
			}
			if pub.startCalls != 0 || pub.restartCalls != 0 {
				t.Fatalf("过期后不得发布：start=%d restart=%d", pub.startCalls, pub.restartCalls)
			}
			status := lastCapturedLeaseStatus(t, client)
			if status.Status != "stopped" || status.ErrorCode != "SOURCE_LEASE_MISSING" || !gw.sessionStopped(command.SessionID) {
				t.Fatalf("过期后必须终止会话并上报关闭原因：%+v", status)
			}
			now := time.Now()
			snapshot.IssuedAt, snapshot.CatalogVersion = now, 2
			snapshot.Sources[0].ExpiresAt = now.Add(time.Minute)
			if err := gw.sources.ApplyCatalog(snapshot, "edge-1", now); err != nil {
				t.Fatal(err)
			}
			gw.beginStarting(command.SessionID)
			gw.startPublisher(ctx, command, true)
			if pub.restartCalls != 0 {
				t.Fatal("续租不能复活已终止的旧会话")
			}
		})
	}
}

// catalogChangingClient 在 publishing 消息确认窗口改变目录，验证临近发布调用的第二次检查。
type catalogChangingClient struct {
	*captureClient
	change func()
}

func (c *catalogChangingClient) Publish(topic string, qos byte, retained bool, body interface{}) paho.Token {
	token := c.captureClient.Publish(topic, qos, retained, body)
	var status model.StatusMessage
	_ = json.Unmarshal(body.([]byte), &status)
	if status.Status == "publishing" {
		c.change()
	}
	return token
}

func TestLeaseRevokedWhilePublishingStatusConfirmedCannotStart(t *testing.T) {
	gw, pub, snapshot := leaseLifecycleGateway(t, fakeProber{}, time.Now().Add(time.Minute))
	client := &captureClient{}
	gw.mqtt = &catalogChangingClient{captureClient: client, change: func() {
		now := time.Now()
		snapshot.IssuedAt, snapshot.CatalogVersion, snapshot.Sources = now, 2, nil
		if err := gw.sources.ApplyCatalog(snapshot, "edge-1", now); err != nil {
			t.Fatal(err)
		}
	}}
	command := mediaCommand("FIXED_CAMERA", "ack-window-session")
	if err := gw.validateMediaStart(command); err != nil {
		t.Fatal(err)
	}
	gw.beginStarting(command.SessionID)
	gw.start(context.Background(), command)
	if pub.startCalls != 0 {
		t.Fatal("确认 publishing 期间撤权后不得启动发布进程")
	}
	status := lastCapturedLeaseStatus(t, client)
	if status.Status != "stopped" || status.ErrorCode != "SOURCE_LEASE_MISSING" {
		t.Fatalf("撤权原因未闭环：%+v", status)
	}
}

func TestChangedAddressDuringPublishingRequiresNewProbe(t *testing.T) {
	gw, pub, snapshot := leaseLifecycleGateway(t, fakeProber{}, time.Now().Add(time.Minute))
	client := &captureClient{}
	gw.mqtt = &catalogChangingClient{captureClient: client, change: func() {
		now := time.Now()
		snapshot.IssuedAt, snapshot.CatalogVersion = now, 2
		snapshot.Sources[0].MainURL = "rtsp://camera.example/new-main"
		if err := gw.sources.ApplyCatalog(snapshot, "edge-1", now); err != nil {
			t.Fatal(err)
		}
	}}
	command := mediaCommand("FIXED_CAMERA", "changed-input-session")
	if err := gw.validateMediaStart(command); err != nil {
		t.Fatal(err)
	}
	gw.beginStarting(command.SessionID)
	gw.start(context.Background(), command)
	if pub.startCalls != 0 {
		t.Fatal("已变化的地址不能复用旧输入探测结果")
	}
	if status := lastCapturedLeaseStatus(t, client); status.ErrorCode != "SOURCE_INPUT_CHANGED" {
		t.Fatalf("缺少明确输入变化原因：%+v", status)
	}
}

func TestDisconnectedExpiryKeepsTerminalReasonUntilReconnect(t *testing.T) {
	expiry := time.Now().Add(300 * time.Millisecond)
	gw, _, _ := leaseLifecycleGateway(t, fakeProber{}, expiry)
	command := mediaCommand("FIXED_CAMERA", "offline-session")
	if err := gw.validateMediaStart(command); err != nil {
		t.Fatal(err)
	}
	gw.leaseBoundSessions[command.SessionID] = true
	gw.rememberSession(command.SessionID)
	gw.handleConnectionLost()
	time.Sleep(time.Until(expiry) + 20*time.Millisecond)
	gw.sweepRevokedSources()
	if status := gw.pendingStatuses[command.SessionID]; status.status != "stopped" || status.errorCode != "SOURCE_LEASE_MISSING" {
		t.Fatalf("断网不能丢失终止事实：%+v", status)
	}
	// 即使再次断线或启动/停止清理尚未结束，也不能把撤权原因降级为普通中断。
	gw.beginStarting(command.SessionID)
	gw.beginStopping(command.SessionID)
	gw.handleConnectionLost()
	client := &captureClient{}
	gw.mqtt = client
	gw.flushPendingStatuses()
	status := lastCapturedLeaseStatus(t, client)
	if status.Status != "stopped" || status.ErrorCode != "SOURCE_LEASE_MISSING" || status.SourceID != command.SourceID {
		t.Fatalf("重连后应补报精确终止原因：%+v", status)
	}
	if len(gw.pendingStatuses) != 0 {
		t.Fatal("确认成功后应清除待补报状态")
	}
}

type failedStatusToken struct{ completedToken }

func (failedStatusToken) Error() error { return errors.New("测试 MQTT 发送失败") }

type failingStatusClient struct{ *captureClient }

func (c *failingStatusClient) Publish(topic string, qos byte, retained bool, body interface{}) paho.Token {
	c.captureClient.Publish(topic, qos, retained, body)
	return failedStatusToken{}
}

func TestLeaseTerminalPublishFailureIsRetriedAndConfirmedOnce(t *testing.T) {
	gw, _, _ := leaseLifecycleGateway(t, fakeProber{}, time.Now().Add(time.Minute))
	command := mediaCommand("FIXED_CAMERA", "failed-report-session")
	if err := gw.validateMediaStart(command); err != nil {
		t.Fatal(err)
	}
	gw.mqtt = &failingStatusClient{captureClient: &captureClient{}}
	gw.revokeMediaSession(command.SessionID, "SOURCE_LEASE_MISSING")
	gw.flushPendingStatuses()
	if len(gw.pendingStatuses) != 1 {
		t.Fatal("发送失败必须保留待补报终止状态")
	}
	client := &captureClient{}
	gw.mqtt = client
	gw.flushPendingStatuses()
	gw.flushPendingStatuses()
	if len(client.messages) != 1 || len(gw.pendingStatuses) != 0 {
		t.Fatal("成功确认后不得反复补报")
	}
	if status := lastCapturedLeaseStatus(t, client); status.ErrorCode != "SOURCE_LEASE_MISSING" {
		t.Fatalf("重试丢失撤权原因：%+v", status)
	}
}

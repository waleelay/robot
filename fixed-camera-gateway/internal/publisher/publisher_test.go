package publisher

import (
	"bytes"
	"context"
	"errors"
	"log"
	"os/exec"
	"strings"
	"testing"
	"time"

	"fixed-camera-gateway/internal/config"
	"fixed-camera-gateway/internal/model"
)

func TestPublisherLogDoesNotContainTokenOrRTSPURL(t *testing.T) {
	var output bytes.Buffer
	previousWriter := log.Writer()
	previousFlags := log.Flags()
	log.SetOutput(&output)
	log.SetFlags(0)
	t.Cleanup(func() {
		log.SetOutput(previousWriter)
		log.SetFlags(previousFlags)
	})

	pub := NewProcessPublisher(config.Config{PublisherCmd: "/bin/echo {token} {rtsp}"})
	command := model.StartCommand{
		SessionID:      "session-log",
		Channel:        "visible",
		Quality:        "sub",
		PublisherToken: "publisher-token-secret",
		RTSPURL:        "rtsp://user:password@camera.example/live",
		ExpiresAt:      time.Now().Add(time.Minute),
	}
	_, _, _ = pub.Start(context.Background(), command, command.RTSPURL)

	logs := output.String()
	if !strings.Contains(logs, "推流进程已启动") {
		t.Fatalf("应输出中文推流启动日志，实际日志=%q", logs)
	}
	for _, secret := range []string{"publisher-token-secret", "rtsp://", "password", "camera.example"} {
		if strings.Contains(logs, secret) {
			t.Fatalf("日志不得包含敏感信息 %q，实际日志=%q", secret, logs)
		}
	}
}

func TestProcessDiagnosticsRedactsSensitiveValues(t *testing.T) {
	entry := &processEntry{
		stderr: &tailBuffer{},
		redactValues: []string{
			"publisher-token-secret",
			"rtsp://user:password@camera.example/live",
			"wss://livekit.example/rtc",
		},
	}
	_, _ = entry.stderr.Write([]byte("connect rtsp://user:password@camera.example/live token=publisher-token-secret livekit=wss://livekit.example/rtc failed"))

	diagnostics := entry.diagnostics()
	for _, secret := range []string{"publisher-token-secret", "rtsp://", "password", "camera.example", "livekit.example"} {
		if strings.Contains(diagnostics, secret) {
			t.Fatalf("诊断日志不得包含敏感信息 %q，实际=%q", secret, diagnostics)
		}
	}
	if !strings.Contains(diagnostics, "connect") || !strings.Contains(diagnostics, "failed") {
		t.Fatalf("应保留可诊断的错误上下文，实际=%q", diagnostics)
	}
}

func TestProcessDiagnosticsCollectsStdoutAndStderr(t *testing.T) {
	cmd := exec.Command("/bin/sh", "-c", "echo stdout-message; echo stderr-message >&2; exit 1")
	entry := newProcessEntry(cmd, "test", time.Now().Add(time.Minute), model.StartCommand{}, "")
	if err := cmd.Start(); err != nil {
		t.Fatal(err)
	}
	entry.startWaiting()
	result := <-entry.done
	if result.err == nil {
		t.Fatal("测试进程应异常退出")
	}
	for _, expected := range []string{"stdout-message", "stderr-message"} {
		if !strings.Contains(result.diagnostics, expected) {
			t.Fatalf("应保留 %q，实际=%q", expected, result.diagnostics)
		}
	}
}

func TestWithoutProxyEnvKeepsNonProxyConfiguration(t *testing.T) {
	environment := withoutProxyEnv([]string{
		"HTTP_PROXY=http://127.0.0.1:7892",
		"https_proxy=http://127.0.0.1:7892",
		"ALL_PROXY=socks5://127.0.0.1:7892",
		"NO_PROXY=localhost",
		"GIO_USE_PROXY_RESOLVER=dummy",
		"GSTREAMER_PUBLISHER_PATH=/tmp/gstreamer-publisher",
	})
	actual := strings.Join(environment, "\n")
	for _, name := range []string{"HTTP_PROXY", "https_proxy", "ALL_PROXY", "NO_PROXY"} {
		if strings.Contains(actual, name+"=") {
			t.Fatalf("推流子进程不得继承受控环境变量 %s，实际=%q", name, actual)
		}
	}
	for _, expected := range []string{"GIO_USE_PROXY_RESOLVER=dummy", "GSTREAMER_PUBLISHER_PATH=/tmp/gstreamer-publisher"} {
		if !strings.Contains(actual, expected) {
			t.Fatalf("非代理环境变量应保留 %q，实际=%q", expected, actual)
		}
	}
}

func TestShouldStartWithFFmpegDuringGStreamerRetryCooldown(t *testing.T) {
	pub := NewProcessPublisher(config.Config{
		PublisherMode:          "auto",
		FFmpegPublisherCmd:     "ffmpeg-publisher",
		PublisherRetryInterval: time.Minute,
	})
	rtspURL := "rtsp://camera/live"
	pub.gstreamerFailedRTSPURL[rtspURL] = time.Now()

	if !pub.shouldStartWithFFmpeg(model.StartCommand{SessionID: "session-1"}, rtspURL) {
		t.Fatal("GStreamer 失败冷却期内应优先使用 FFmpeg")
	}
}

func TestShouldRetryGStreamerAfterCooldown(t *testing.T) {
	pub := NewProcessPublisher(config.Config{
		PublisherMode:          "auto",
		FFmpegPublisherCmd:     "ffmpeg-publisher",
		PublisherRetryInterval: time.Minute,
	})
	rtspURL := "rtsp://camera/live"
	pub.gstreamerFailedRTSPURL[rtspURL] = time.Now().Add(-2 * time.Minute)

	if pub.shouldStartWithFFmpeg(model.StartCommand{SessionID: "session-1"}, rtspURL) {
		t.Fatal("冷却期结束后应重新尝试 GStreamer")
	}
	if _, exists := pub.gstreamerFailedRTSPURL[rtspURL]; exists {
		t.Fatal("过期的 GStreamer 失败记录应被清理")
	}
}

func TestStreamKeyUsesCameraAndRoomInsteadOfSession(t *testing.T) {
	first := model.StartCommand{
		SessionID:  "session-1",
		SourceType: "FIXED_CAMERA",
		SourceID:   "camera-1",
		RoomName:   "room-camera-1",
	}
	second := first
	second.SessionID = "session-2"

	if streamKey(first) != streamKey(second) {
		t.Fatal("同一路摄像头的不同观看会话应复用同一个推流资源")
	}
}

func TestStopOnlyStopsAfterLastSessionLeaves(t *testing.T) {
	pub := NewProcessPublisher(config.Config{})
	key := "FIXED_CAMERA|camera-1|room-camera-1"
	pub.streamSessions[key] = map[string]struct{}{"session-1": {}, "session-2": {}}
	pub.sessions["session-1"] = key
	pub.sessions["session-2"] = key

	if err := pub.Stop("session-1"); err != nil {
		t.Fatal(err)
	}
	if len(pub.streamSessions[key]) != 1 {
		t.Fatalf("第一个观看者停止后仍应保留推流资源，实际会话数=%d", len(pub.streamSessions[key]))
	}
	if err := pub.Stop("session-2"); err != nil {
		t.Fatal(err)
	}
	if _, exists := pub.streamSessions[key]; exists {
		t.Fatal("最后一个观看者停止后应清理资源会话映射")
	}
}

func TestRejectsMissingPublisherTokenExpiry(t *testing.T) {
	pub := NewProcessPublisher(config.Config{PublisherCmd: "/bin/true"})
	_, _, err := pub.Start(context.Background(), model.StartCommand{
		SessionID: "session-no-expiry", PublisherToken: "token",
	}, "rtsp://camera/live")
	if err == nil || !strings.Contains(err.Error(), "Token") {
		t.Fatalf("缺少 Token 到期时间时应拒绝启动，实际错误=%v", err)
	}
}

func TestTokenExpiryCleansPublisherAndAllBoundSessions(t *testing.T) {
	pub := NewProcessPublisher(config.Config{})
	key := "FIXED_CAMERA|camera-1|room-1"
	entry := &processEntry{done: make(chan processResult, 1), mode: "test", expiresAt: time.Now().Add(20 * time.Millisecond)}
	pub.cmds[key] = entry
	pub.bindSessionLocked("session-1", key)
	pub.bindSessionLocked("session-2", key)
	pub.watchTokenExpiry(key, entry, "session-1")

	time.Sleep(80 * time.Millisecond)
	stats := pub.Snapshot()
	if stats.ActivePublishers != 0 || stats.ActiveSessions != 0 || stats.TokenExpirations != 1 {
		t.Fatalf("Token 到期后应完整清理，实际=%+v", stats)
	}
	select {
	case event := <-pub.Events():
		if event.ReasonCode != "PUBLISH_TOKEN_EXPIRED" || len(event.SessionIDs) != 2 {
			t.Fatalf("Token 到期事件不完整，实际=%+v", event)
		}
	default:
		t.Fatal("Token 到期后应通知上层回写会话状态")
	}
}

func TestUnexpectedProcessExitCleansPublisherMappings(t *testing.T) {
	pub := NewProcessPublisher(config.Config{})
	key := "FIXED_CAMERA|camera-1|room-1"
	entry := &processEntry{done: make(chan processResult, 1), mode: "test", expiresAt: time.Now().Add(time.Minute)}
	pub.cmds[key] = entry
	pub.bindSessionLocked("session-1", key)
	pub.watchProcessExit(key, entry, "session-1")
	entry.done <- processResult{err: errors.New("模拟异常退出"), exitedAt: time.Now()}

	time.Sleep(20 * time.Millisecond)
	stats := pub.Snapshot()
	if stats.ActivePublishers != 0 || stats.ActiveSessions != 0 || stats.UnexpectedExits != 1 {
		t.Fatalf("进程异常退出后应完整清理，实际=%+v", stats)
	}
	select {
	case event := <-pub.Events():
		if event.ReasonCode != "PUBLISH_PROCESS_EXITED" || len(event.SessionIDs) != 1 {
			t.Fatalf("进程退出事件不完整，实际=%+v", event)
		}
	default:
		t.Fatal("进程异常退出后应通知上层回写会话状态")
	}
}

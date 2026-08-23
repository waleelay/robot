package publisher

import (
	"bytes"
	"context"
	"log"
	"strings"
	"testing"
	"time"

	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
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

func TestShouldStartWithFFmpegDuringGStreamerRetryCooldown(t *testing.T) {
	pub := NewProcessPublisher(config.Config{
		PublisherMode:           "auto",
		FFmpegPublisherCmd:      "ffmpeg-publisher",
		PublisherGStreamerRetry: time.Minute,
	})
	rtspURL := "rtsp://camera/live"
	pub.gstreamerFailedRTSPURL[rtspURL] = time.Now()

	if !pub.shouldStartWithFFmpeg(model.StartCommand{SessionID: "session-1"}, rtspURL) {
		t.Fatal("GStreamer 失败冷却期内应优先使用 FFmpeg")
	}
}

func TestShouldRetryGStreamerAfterCooldown(t *testing.T) {
	pub := NewProcessPublisher(config.Config{
		PublisherMode:           "auto",
		FFmpegPublisherCmd:      "ffmpeg-publisher",
		PublisherGStreamerRetry: time.Minute,
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

func TestShouldKeepExplicitFFmpegFirstDevice(t *testing.T) {
	pub := NewProcessPublisher(config.Config{
		PublisherMode:           "auto",
		FFmpegPublisherCmd:      "ffmpeg-publisher",
		PublisherGStreamerRetry: time.Minute,
		PublisherFFmpegFirstIDs: map[string]bool{"camera-1": true},
	})

	if !pub.shouldStartWithFFmpeg(model.StartCommand{DeviceID: "camera-1"}, "rtsp://camera/live") {
		t.Fatal("人工配置的 FFmpeg 优先设备应始终使用 FFmpeg")
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

func TestStopStreamKeyMatchesStartKey(t *testing.T) {
	start := model.StartCommand{
		SourceType: "FIXED_CAMERA",
		SourceID:   "camera-1",
		RoomName:   "room-camera-1",
		Channel:    "visible",
		Quality:    "sub",
	}
	stop := model.StopCommand{
		SourceType: "FIXED_CAMERA",
		SourceID:   "camera-1",
		RoomName:   "room-camera-1",
	}

	if streamKey(start) != stopStreamKey(stop) {
		t.Fatal("停止命令应能定位启动命令创建的推流资源")
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

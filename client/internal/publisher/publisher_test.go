package publisher

import (
	"testing"
	"time"

	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
)

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

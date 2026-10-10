package config

import (
	"strings"
	"testing"
)

func TestPublisherTemplatesRequireNeutralInputPlaceholder(t *testing.T) {
	for _, field := range []string{"custom", "ffmpeg", "gstreamer"} {
		t.Run(field, func(t *testing.T) {
			cfg := Config{}
			switch field {
			case "custom":
				cfg.PublisherCmd = "publisher {rtsp}"
			case "ffmpeg":
				cfg.FFmpegPublisherCmd = "ffmpeg-publisher {rtsp}"
			case "gstreamer":
				cfg.GStreamerPipeline = "rtspsrc location={rtsp}"
			}
			if err := cfg.ValidatePublisherTemplates(); err == nil || !strings.Contains(err.Error(), "{input}") {
				t.Fatalf("旧模板应在启动前明确拒绝并提示迁移：%v", err)
			}
		})
	}
	if err := (Config{PublisherCmd: "publisher {input}", FFmpegPublisherCmd: "ffmpeg-publisher {input}", GStreamerPipeline: "rtspsrc location={input}"}).ValidatePublisherTemplates(); err != nil {
		t.Fatal(err)
	}
}

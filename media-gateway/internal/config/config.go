package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config 包含统一媒体入口的运行身份、可选本地配置与发布参数。
type Config struct {
	GatewayID              string
	ClientID               string
	MQTTBroker             string
	MQTTUsername           string
	MQTTPassword           string
	HeartbeatInterval      time.Duration
	HealthProbeInterval    time.Duration
	HealthProbeWorkers     int
	HTTPAddr               string
	FFprobePath            string
	ProbeTimeout           time.Duration
	PublisherCmd           string
	PublisherMode          string
	PublisherFallbackWatch time.Duration
	PublisherRetryInterval time.Duration
	PublisherStopTimeout   time.Duration
	FFmpegPublisherCmd     string
	GStreamerPublisherPath string
	GStreamerPipeline      string
	// SourcesPath 是可选受控本地配置路径；固定摄像头始终要求平台目录租约。
	SourcesPath string
}

// ValidatePublisherTemplates 防止升级后旧占位符未替换而把错误地址交给发布进程。
func (c Config) ValidatePublisherTemplates() error {
	for _, template := range []string{c.PublisherCmd, c.FFmpegPublisherCmd, c.GStreamerPipeline} {
		if strings.Contains(template, "{rtsp}") {
			return fmt.Errorf("发布模板仍包含旧占位符 {rtsp}，请替换为 {input}")
		}
	}
	return nil
}

func Load() Config {
	return Config{
		SourcesPath:            env("MEDIA_GATEWAY_SOURCES_FILE", ""),
		GatewayID:              env("MEDIA_GATEWAY_ID", "default"),
		ClientID:               env("MEDIA_GATEWAY_CLIENT_ID", "media-gateway-"+env("MEDIA_GATEWAY_ID", "default")),
		MQTTBroker:             env("MQTT_BROKER_URL", "tcp://127.0.0.1:1883"),
		MQTTUsername:           env("MQTT_USERNAME", ""),
		MQTTPassword:           env("MQTT_PASSWORD", ""),
		HeartbeatInterval:      time.Duration(envInt("MEDIA_GATEWAY_HEARTBEAT_INTERVAL_SECONDS", 10)) * time.Second,
		HealthProbeInterval:    time.Duration(envInt("MEDIA_GATEWAY_HEALTH_PROBE_INTERVAL_SECONDS", 60)) * time.Second,
		HealthProbeWorkers:     envInt("MEDIA_GATEWAY_HEALTH_PROBE_CONCURRENCY", 4),
		HTTPAddr:               env("MEDIA_GATEWAY_HTTP_ADDR", "127.0.0.1:9092"),
		FFprobePath:            env("FFPROBE_PATH", "ffprobe"),
		ProbeTimeout:           time.Duration(envInt("PROBE_TIMEOUT_MS", 8000)) * time.Millisecond,
		PublisherCmd:           env("PUBLISHER_CMD", ""),
		PublisherMode:          strings.ToLower(env("PUBLISHER_MODE", "auto")),
		PublisherFallbackWatch: time.Duration(envInt("PUBLISHER_FALLBACK_WATCH_SECONDS", 8)) * time.Second,
		PublisherRetryInterval: time.Duration(envInt("PUBLISHER_GSTREAMER_RETRY_SECONDS", 60)) * time.Second,
		PublisherStopTimeout:   time.Duration(envInt("PUBLISHER_STOP_TIMEOUT_SECONDS", 5)) * time.Second,
		FFmpegPublisherCmd:     env("FFMPEG_PUBLISHER_CMD", "./scripts/ffmpeg-livekit-publisher.sh {input} {livekitUrl} {token}"),
		GStreamerPublisherPath: env("GSTREAMER_PUBLISHER_PATH", "gstreamer-publisher"),
		GStreamerPipeline:      env("GSTREAMER_PIPELINE", "rtspsrc location={input} protocols=tcp latency=100 drop-on-latency=true ! queue max-size-buffers=0 max-size-bytes=0 max-size-time=200000000 leaky=downstream ! rtph264depay ! h264parse config-interval=1 ! video/x-h264,alignment=au ! h264timestamper"),
	}
}

func env(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return fallback
}

func envInt(name string, fallback int) int {
	value, err := strconv.Atoi(strings.TrimSpace(os.Getenv(name)))
	if err != nil {
		return fallback
	}
	return value
}

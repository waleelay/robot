package config

import (
	"os"
	"strconv"
	"time"
)

type Config struct {
	RobotID                string
	MQTTBroker             string
	MQTTUsername           string
	MQTTPassword           string
	ClientID               string
	RTSPVisibleSub         string
	RTSPVisibleMain        string
	RTSPThermalSub         string
	RTSPThermalMain        string
	FFprobePath            string
	PublisherCmd           string
	GStreamerPublisherPath string
	GStreamerPipeline      string
	ProbeTimeout           time.Duration
}

func Load() Config {
	return Config{
		RobotID:         env("ROBOT_ID", "robot-001"),
		MQTTBroker:      env("MQTT_BROKER_URL", "tcp://localhost:1883"),
		MQTTUsername:    env("MQTT_USERNAME", ""),
		MQTTPassword:    env("MQTT_PASSWORD", ""),
		ClientID:        env("ROBOT_CLIENT_ID", "robot-media-client"),
		RTSPVisibleSub:  env("RTSP_VISIBLE_SUB", "rtsp://192.168.124.204:8554/camera01"),
		RTSPVisibleMain: env("RTSP_VISIBLE_MAIN", "rtsp://192.168.124.204:8554/camera01"),
		RTSPThermalSub:  env("RTSP_THERMAL_SUB", "rtsp://192.168.124.204:8554/camera01"),
		RTSPThermalMain: env("RTSP_THERMAL_MAIN", "rtsp://192.168.124.204:8554/camera01"),
		FFprobePath:            env("FFPROBE_PATH", "ffprobe"),
		PublisherCmd:           env("PUBLISHER_CMD", ""),
		GStreamerPublisherPath: env("GSTREAMER_PUBLISHER_PATH", "gstreamer-publisher"),
		GStreamerPipeline:      env("GSTREAMER_PIPELINE", "rtspsrc location={rtsp} protocols=tcp latency=100 ! queue ! rtph264depay ! h264parse config-interval=1"),
		ProbeTimeout:           time.Duration(envInt("PROBE_TIMEOUT_MS", 8000)) * time.Millisecond,
	}
}

func env(key, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}

func envInt(key string, fallback int) int {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	n, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return n
}

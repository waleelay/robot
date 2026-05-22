package config

import (
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	RobotID                string
	RobotName              string
	RobotType              string
	MQTTBroker             string
	MQTTUsername           string
	MQTTPassword           string
	ClientID               string
	RTSPVisibleSub         string
	RTSPVisibleMain        string
	RTSPThermalSub         string
	RTSPThermalMain        string
	Cameras                []Camera
	FFprobePath            string
	PublisherCmd           string
	GStreamerPublisherPath string
	GStreamerPipeline      string
	ProbeTimeout           time.Duration
	HeartbeatInterval      time.Duration
}

type Camera struct {
	CameraID string
	DeviceID string
	Name     string
	Channel  string
	Quality  string
	RTSPURL  string
}

func Load() Config {
	robotID := env("ROBOT_ID", "robot-001")
	return Config{
		RobotID:                robotID,
		RobotName:              env("ROBOT_NAME", defaultRobotName(robotID)),
		RobotType:              env("ROBOT_TYPE", defaultRobotType(robotID)),
		MQTTBroker:             env("MQTT_BROKER_URL", "tcp://192.168.124.77:1883"),
		MQTTUsername:           env("MQTT_USERNAME", ""),
		MQTTPassword:           env("MQTT_PASSWORD", ""),
		ClientID:               env("ROBOT_CLIENT_ID", "robot-media-client"),
		RTSPVisibleSub:         env("RTSP_VISIBLE_SUB", "rtsp://192.168.124.204:8554/camera01"),
		RTSPVisibleMain:        env("RTSP_VISIBLE_MAIN", "rtsp://192.168.124.204:8554/camera01"),
		RTSPThermalSub:         env("RTSP_THERMAL_SUB", "rtsp://192.168.124.204:8554/camera01"),
		RTSPThermalMain:        env("RTSP_THERMAL_MAIN", "rtsp://192.168.124.204:8554/camera01"),
		Cameras:                cameras(robotID),
		FFprobePath:            env("FFPROBE_PATH", "ffprobe"),
		PublisherCmd:           env("PUBLISHER_CMD", ""),
		GStreamerPublisherPath: env("GSTREAMER_PUBLISHER_PATH", "gstreamer-publisher"),
		GStreamerPipeline:      env("GSTREAMER_PIPELINE", "rtspsrc location={rtsp} protocols=tcp latency=100 ! queue ! rtph264depay ! h264parse config-interval=1"),
		ProbeTimeout:           time.Duration(envInt("PROBE_TIMEOUT_MS", 8000)) * time.Millisecond,
		HeartbeatInterval:      time.Duration(envInt("HEARTBEAT_INTERVAL_MS", 5000)) * time.Millisecond,
	}
}

func cameras(robotID string) []Camera {
	ids := []string{"camera01", "camera02", "camera03"}
	names := []string{"前向双光云台", "后向广角相机", "机械臂腕部相机"}
	if robotID == "robot-002" {
		ids = []string{"camera04", "camera05", "camera06"}
		names = []string{"头部双光云台", "腹部导航相机", "尾部避障相机"}
	}
	result := make([]Camera, 0, len(ids))
	for i, id := range ids {
		result = append(result, Camera{
			CameraID: id,
			DeviceID: id,
			Name:     env("CAMERA_"+strings.ToUpper(id)+"_NAME", names[i]),
			Channel:  "visible",
			Quality:  "sub",
			RTSPURL:  env("RTSP_"+strings.ToUpper(id), "rtsp://192.168.124.204:8554/"+id),
		})
	}
	return result
}

func defaultRobotName(robotID string) string {
	if robotID == "robot-002" {
		return "云深处四足机器狗"
	}
	return "松灵四轮机器人"
}

func defaultRobotType(robotID string) string {
	if robotID == "robot-002" {
		return "四足机器人"
	}
	return "轮式机器人"
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

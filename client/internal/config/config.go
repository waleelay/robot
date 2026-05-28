package config

import (
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	RobotID                  string
	RobotName                string
	RobotType                string
	Battery                  int
	MQTTBroker               string
	MQTTUsername             string
	MQTTPassword             string
	ClientID                 string
	RTSPVisibleSub           string
	RTSPVisibleMain          string
	RTSPThermalSub           string
	RTSPThermalMain          string
	Cameras                  []Camera
	FFprobePath              string
	PublisherCmd             string
	GStreamerPublisherPath   string
	GStreamerPipeline        string
	GSTLaunchPath            string
	AudioCapturePipeline     string
	AudioPlaybackPipeline    string
	ProbeTimeout             time.Duration
	HeartbeatInterval        time.Duration
	RecordingUploadEnabled   bool
	MediaServiceURL          string
	RecordingDirectory       string
	RecordingManifestPath    string
	RecordingDeviceID        string
	UploadScanInterval       time.Duration
	UploadPartConcurrency    int
	LocalCacheMaxBytes       int64
	LocalMinFreeBytes        int64
	LocalRetentionAfterReady time.Duration
}

type Camera struct {
	CameraID  string
	DeviceID  string
	Name      string
	GroupType string
	Channel   string
	Quality   string
	RTSPURL   string
}

func Load() Config {
	robotID := env("ROBOT_ID", "robot-001")
	return Config{
		RobotID:                  robotID,
		RobotName:                env("ROBOT_NAME", defaultRobotName(robotID)),
		RobotType:                env("ROBOT_TYPE", defaultRobotType(robotID)),
		Battery:                  boundedPercentage(envInt("ROBOT_BATTERY", 100)),
		MQTTBroker:               env("MQTT_BROKER_URL", "tcp://192.168.124.77:1883"),
		MQTTUsername:             env("MQTT_USERNAME", ""),
		MQTTPassword:             env("MQTT_PASSWORD", ""),
		ClientID:                 env("ROBOT_CLIENT_ID", "robot-media-client"),
		RTSPVisibleSub:           env("RTSP_VISIBLE_SUB", "rtsp://192.168.124.204:8554/camera01"),
		RTSPVisibleMain:          env("RTSP_VISIBLE_MAIN", "rtsp://192.168.124.204:8554/camera01"),
		RTSPThermalSub:           env("RTSP_THERMAL_SUB", "rtsp://192.168.124.204:8554/camera01"),
		RTSPThermalMain:          env("RTSP_THERMAL_MAIN", "rtsp://192.168.124.204:8554/camera01"),
		Cameras:                  cameras(robotID),
		FFprobePath:              env("FFPROBE_PATH", "ffprobe"),
		PublisherCmd:             env("PUBLISHER_CMD", ""),
		GStreamerPublisherPath:   env("GSTREAMER_PUBLISHER_PATH", "gstreamer-publisher"),
		GStreamerPipeline:        env("GSTREAMER_PIPELINE", "rtspsrc location={rtsp} protocols=tcp latency=100 ! queue ! rtph264depay ! h264parse config-interval=1"),
		GSTLaunchPath:            env("GST_LAUNCH_PATH", "gst-launch-1.0"),
		AudioCapturePipeline:     env("AUDIO_CAPTURE_PIPELINE", "autoaudiosrc ! audioconvert ! audioresample ! audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved ! fdsink fd=1"),
		AudioPlaybackPipeline:    env("AUDIO_PLAYBACK_PIPELINE", "fdsrc fd=0 ! audio/x-raw,format=S16LE,rate=48000,channels=1,layout=interleaved ! audioconvert ! audioresample ! autoaudiosink"),
		ProbeTimeout:             time.Duration(envInt("PROBE_TIMEOUT_MS", 8000)) * time.Millisecond,
		HeartbeatInterval:        time.Duration(envInt("HEARTBEAT_INTERVAL_MS", 5000)) * time.Millisecond,
		RecordingUploadEnabled:   envBool("RECORDING_UPLOAD_ENABLED", true),
		MediaServiceURL:          env("MEDIA_SERVICE_URL", "http://192.168.124.77:8088"),
		RecordingDirectory:       env("RECORDING_DIRECTORY", "./recordings"),
		RecordingManifestPath:    env("RECORDING_MANIFEST_PATH", "./recording-upload-manifest.json"),
		RecordingDeviceID:        env("RECORDING_DEVICE_ID", "camera01"),
		UploadScanInterval:       time.Duration(envInt("RECORDING_UPLOAD_SCAN_INTERVAL_MS", 30000)) * time.Millisecond,
		UploadPartConcurrency:    envInt("RECORDING_UPLOAD_PART_CONCURRENCY", 2),
		LocalCacheMaxBytes:       envInt64("RECORDING_LOCAL_CACHE_MAX_BYTES", 107374182400),
		LocalMinFreeBytes:        envInt64("RECORDING_LOCAL_MIN_FREE_BYTES", 10737418240),
		LocalRetentionAfterReady: time.Duration(envInt("RECORDING_LOCAL_RETENTION_AFTER_READY_HOURS", 24)) * time.Hour,
	}
}

func cameras(robotID string) []Camera {
	ids := []string{"camera01", "camera02", "camera03"}
	names := []string{"前向双光云台", "后向广角相机", "机械臂腕部相机"}
	groupTypes := []string{"dual_gimbal", "body", "arm"}
	if robotID == "robot-002" {
		ids = []string{"camera04", "camera05", "camera06"}
		names = []string{"头部双光云台", "腹部导航相机", "尾部避障相机"}
		groupTypes = []string{"dual_gimbal", "body", "body"}
	}
	result := make([]Camera, 0, len(ids))
	for i, id := range ids {
		result = append(result, Camera{
			CameraID:  id,
			DeviceID:  id,
			Name:      env("CAMERA_"+strings.ToUpper(id)+"_NAME", names[i]),
			GroupType: env("CAMERA_"+strings.ToUpper(id)+"_GROUP_TYPE", groupTypes[i]),
			Channel:   "visible",
			Quality:   "sub",
			RTSPURL:   env("RTSP_"+strings.ToUpper(id), "rtsp://192.168.124.204:8554/"+id),
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

func envInt64(key string, fallback int64) int64 {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	n, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return fallback
	}
	return n
}

func envBool(key string, fallback bool) bool {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	result, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return result
}

func boundedPercentage(value int) int {
	if value < 0 {
		return 0
	}
	if value > 100 {
		return 100
	}
	return value
}

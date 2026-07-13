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
	Type                     string
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
	Devices                  []Device
	FFprobePath              string
	PublisherCmd             string
	PublisherMode            string
	PublisherFallbackWatch   time.Duration
	PublisherFFmpegFirstIDs  map[string]bool
	FFmpegPublisherCmd       string
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
	UploadPartURLBatchSize   int
	UploadFileConcurrency    int
	LocalCacheMaxBytes       int64
	LocalMinFreeBytes        int64
	LocalRetentionAfterReady time.Duration
}

type Camera struct {
	CameraID    string
	DeviceID    string
	Name        string
	GroupType   string
	Channel     string
	Quality     string
	RTSPURL     string
	RTSPMainURL string
	RTSPSubURL  string
}

type Device struct {
	DeviceID       string
	BindingID      string
	Scope          string
	DeviceType     string
	DisplayName    string
	Vendor         string
	Model          string
	OnlineStatus   string
	ControlStatus  string
	Enabled        bool
	RiskLevel      string
	Actions        []string
	Status         map[string]any
	ControlProfile map[string]any
}

func Load() Config {
	robotID := env("ROBOT_ID", "test001")
	return Config{
		RobotID:                  robotID,
		RobotName:                env("ROBOT_NAME", defaultRobotName(robotID)),
		Type:                     env("ROBOT_TYPE", defaultType(robotID)),
		Battery:                  boundedPercentage(envInt("ROBOT_BATTERY", 100)),
		MQTTBroker:               env("MQTT_BROKER_URL", "tcp://192.168.124.77:1883"),
		MQTTUsername:             env("MQTT_USERNAME", ""),
		MQTTPassword:             env("MQTT_PASSWORD", ""),
		ClientID:                 env("ROBOT_CLIENT_ID", "robot-media-client"),
		RTSPVisibleSub:           env("RTSP_VISIBLE_SUB", "rtsp://admin:okwy1688@192.168.1.64:554/Streaming/Channels/102"),
		RTSPVisibleMain:          env("RTSP_VISIBLE_MAIN", "rtsp://admin:okwy1688@192.168.1.64:554/Streaming/Channels/101"),
		RTSPThermalSub:           env("RTSP_THERMAL_SUB", "rtsp://admin:okwy1688@192.168.1.65:554/Streaming/Channels/102"),
		RTSPThermalMain:          env("RTSP_THERMAL_MAIN", "rtsp://admin:okwy1688@192.168.1.65:554/Streaming/Channels/102"),
		Cameras:                  cameras(robotID),
		Devices:                  devices(robotID),
		FFprobePath:              env("FFPROBE_PATH", "ffprobe"),
		PublisherCmd:             env("PUBLISHER_CMD", ""),
		PublisherMode:            strings.ToLower(env("PUBLISHER_MODE", "auto")),
		PublisherFallbackWatch:   time.Duration(envInt("PUBLISHER_FALLBACK_WATCH_SECONDS", 8)) * time.Second,
		PublisherFFmpegFirstIDs:  envCSVSet("PUBLISHER_FFMPEG_FIRST_DEVICE_IDS", ""),
		FFmpegPublisherCmd:       env("FFMPEG_PUBLISHER_CMD", "./scripts/ffmpeg-livekit-publisher.sh {rtsp} {livekitUrl} {token}"),
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
		UploadPartConcurrency:    envInt("RECORDING_UPLOAD_PART_CONCURRENCY", 4),
		UploadPartURLBatchSize:   envInt("RECORDING_UPLOAD_PART_URL_BATCH_SIZE", 16),
		UploadFileConcurrency:    envInt("RECORDING_UPLOAD_FILE_CONCURRENCY", 2),
		LocalCacheMaxBytes:       envInt64("RECORDING_LOCAL_CACHE_MAX_BYTES", 107374182400),
		LocalMinFreeBytes:        envInt64("RECORDING_LOCAL_MIN_FREE_BYTES", 10737418240),
		LocalRetentionAfterReady: time.Duration(envInt("RECORDING_LOCAL_RETENTION_AFTER_READY_HOURS", 24)) * time.Hour,
	}
}

func devices(robotID string) []Device {
	baseType := "WHEELED_BASE"
	baseVendor := "SONGLING"
	baseModel := "SCOUT"
	maxLinearX := 1.0
	maxLinearY := 0.4
	maxAngularZ := 0.8
	if robotID == "test002" {
		baseType = "QUADRUPED_BASE"
		baseVendor = "DEEPNROBOTICS"
		baseModel = "X30"
		maxLinearX = 0.8
		maxAngularZ = 0.6
	}
	items := []Device{
		{
			DeviceID:      "base",
			BindingID:     "bind-base",
			Scope:         "BODY",
			DeviceType:    baseType,
			DisplayName:   "机器人本体",
			Vendor:        baseVendor,
			Model:         baseModel,
			OnlineStatus:  "online",
			ControlStatus: "idle",
			Enabled:       true,
			Actions:       []string{"drive.velocity", "navigation.return_home", "docking.leave"},
			ControlProfile: map[string]any{
				"maxLinearX":         maxLinearX,
				"maxLinearY":         maxLinearY,
				"maxAngularZ":        maxAngularZ,
				"controlFrameRateHz": 10,
			},
		},
		{
			DeviceID:      "ptz-dual-001",
			BindingID:     "bind-ptz-dual-001",
			Scope:         "PAYLOAD",
			DeviceType:    "DUAL_LIGHT_PTZ",
			DisplayName:   "双光云台",
			Vendor:        "CUSTOM",
			Model:         "DL-PTZ-01",
			OnlineStatus:  "online",
			ControlStatus: "idle",
			Enabled:       true,
			Actions: []string{
				"up", "down", "left", "right",
				"left_up", "right_up", "left_down", "right_down",
				"ptz.auto_rotate", "ptz.home", "camera.zoom",
			},
			Status: map[string]any{
				"autoRotateEnabled": false,
				"panSpeed":          0,
			},
			ControlProfile: map[string]any{
				"maxPanSpeed":        1.0,
				"maxTiltSpeed":       1.0,
				"controlFrameRateHz": 10,
			},
		},
		{
			DeviceID:      "audio-control-001",
			BindingID:     "bind-audio-control-001",
			Scope:         "AUDIO",
			DeviceType:    "SPEAKER",
			DisplayName:   "扬声器",
			OnlineStatus:  "online",
			ControlStatus: "idle",
			Enabled:       true,
			Actions:       []string{"set_volume", "set_mute"},
			Status: map[string]any{
				"volume":        50,
				"volumePercent": 50,
				"muted":         false,
			},
			ControlProfile: map[string]any{
				"step":      5,
				"minVolume": 0,
				"maxVolume": 100,
			},
		},
	}
	if robotID != "robot-unitree-001" {
		items = append(items,
			Device{
				DeviceID:      "launcher-001",
				BindingID:     "bind-launcher-001",
				Scope:         "PAYLOAD",
				DeviceType:    "LAUNCHER",
				DisplayName:   "六联发射器",
				Vendor:        "CUSTOM",
				Model:         "LCH-06",
				OnlineStatus:  "online",
				ControlStatus: "idle",
				Enabled:       true,
				RiskLevel:     "HIGH",
				Actions:       []string{"get_status", "set_safety", "fire"},
				Status:        launcherStatus(),
				ControlProfile: map[string]any{
					"tubes":                []int{1, 2, 3, 4, 5, 6},
					"requiresConfirm":      true,
					"requiresSafetySwitch": true,
				},
			},
			Device{
				DeviceID:      "net-gun-001",
				BindingID:     "bind-net-gun-001",
				Scope:         "PAYLOAD",
				DeviceType:    "NET_GUN",
				DisplayName:   "捕网枪",
				Vendor:        "CUSTOM",
				Model:         "NL-01",
				OnlineStatus:  "online",
				ControlStatus: "idle",
				Enabled:       true,
				RiskLevel:     "HIGH",
				Actions:       []string{"fire"},
				ControlProfile: map[string]any{
					"requiresConfirm": true,
					"cooldownMs":      3000,
				},
			})
	}
	items = append(items,
		Device{
			DeviceID:      "warning-light-left",
			BindingID:     "bind-warning-light-left",
			Scope:         "PAYLOAD",
			DeviceType:    "WARNING_LIGHT",
			DisplayName:   "左警示灯",
			Vendor:        "CUSTOM",
			Model:         "WL-01",
			OnlineStatus:  "online",
			ControlStatus: "idle",
			Enabled:       true,
			Actions:       []string{"get_state", "set_state", "set_mode"},
			Status: map[string]any{
				"enabled": false,
				"powerOn": false,
				"mode":    0,
				"online":  true,
			},
			ControlProfile: map[string]any{
				"lightId":     "light-001",
				"lightIds":    []string{"light-001", "light-002", "all"},
				"modes":       []int{0, 1, 2},
				"supportsAll": true,
			},
		},
		Device{
			DeviceID:      "warning-light-right",
			BindingID:     "bind-warning-light-right",
			Scope:         "PAYLOAD",
			DeviceType:    "WARNING_LIGHT",
			DisplayName:   "右警示灯",
			Vendor:        "CUSTOM",
			Model:         "WL-01",
			OnlineStatus:  "online",
			ControlStatus: "idle",
			Enabled:       true,
			Actions:       []string{"get_state", "set_state", "set_mode"},
			Status: map[string]any{
				"enabled": false,
				"powerOn": false,
				"mode":    0,
				"online":  true,
			},
			ControlProfile: map[string]any{
				"lightId":     "light-002",
				"lightIds":    []string{"light-001", "light-002", "all"},
				"modes":       []int{0, 1, 2},
				"supportsAll": true,
			},
		},
		Device{
			DeviceID:      "vehicle-light",
			BindingID:     "bind-vehicle-light",
			Scope:         "PAYLOAD",
			DeviceType:    "VEHICLE_LIGHT",
			DisplayName:   "车灯光",
			Vendor:        "CUSTOM",
			Model:         "VL-01",
			OnlineStatus:  "online",
			ControlStatus: "idle",
			Enabled:       true,
			Actions:       []string{"light.vehicle.set"},
			ControlProfile: map[string]any{
				"parts":         []string{"front", "rear"},
				"modes":         []string{"OFF", "ON", "BREATH", "CUSTOM"},
				"maxBrightness": 100,
			},
		},
		Device{
			DeviceID:      "intercom-001",
			BindingID:     "bind-intercom-001",
			Scope:         "AUDIO",
			DeviceType:    "INTERCOM",
			DisplayName:   "语音对讲",
			OnlineStatus:  "online",
			ControlStatus: "idle",
			Enabled:       true,
			Actions:       []string{"set_volume", "set_mute"},
			Status: map[string]any{
				"volume":        50,
				"volumePercent": 50,
				"muted":         false,
			},
		})
	return items
}

func launcherStatus() map[string]any {
	return map[string]any{
		"connected":           true,
		"safetySwitchEnabled": false,
		"tubeCount":           6,
		"tubes":               launcherTubes(),
	}
}

func launcherTubes() []map[string]any {
	tubes := make([]map[string]any, 0, 6)
	for tube := 1; tube <= 6; tube++ {
		tubes = append(tubes, map[string]any{
			"tube":      tube,
			"state":     1,
			"stateName": "LOADED",
		})
	}
	return tubes
}

func cameras(robotID string) []Camera {
	ids := []string{"camera01", "camera02", "camera03"}
	names := []string{"云台-可见光", "云台-热成像", "本体相机"}
	groupTypes := []string{"dual_gimbal", "dual_gimbal", "body"}
	if robotID == "test002" {
		ids = []string{"camera04", "camera05", "camera06"}
		names = []string{"头部双光云台", "腹部导航相机", "尾部避障相机"}
		groupTypes = []string{"dual_gimbal", "body", "body"}
	}
	result := make([]Camera, 0, len(ids))
	for i, id := range ids {
		envPrefix := "RTSP_" + strings.ToUpper(id)
		legacyRTSP := env(envPrefix, "rtsp://192.168.124.204:8554/"+id)
		subRTSP := env(envPrefix+"_SUB", legacyRTSP)
		mainRTSP := env(envPrefix+"_MAIN", subRTSP)
		result = append(result, Camera{
			CameraID:    id,
			DeviceID:    id,
			Name:        env("CAMERA_"+strings.ToUpper(id)+"_NAME", names[i]),
			GroupType:   env("CAMERA_"+strings.ToUpper(id)+"_GROUP_TYPE", groupTypes[i]),
			Channel:     "visible",
			Quality:     env("CAMERA_"+strings.ToUpper(id)+"_QUALITY", "auto"),
			RTSPURL:     legacyRTSP,
			RTSPMainURL: mainRTSP,
			RTSPSubURL:  subRTSP,
		})
	}
	return result
}

func defaultRobotName(robotID string) string {
	if robotID == "test002" {
		return "G1四足机器狗"
	}
	return "R1轮式机器人"
}

func defaultType(robotID string) string {
	if robotID == "test002" {
		return "四足机器狗"
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

func envCSVSet(key string, fallback string) map[string]bool {
	raw := os.Getenv(key)
	if raw == "" {
		raw = fallback
	}
	result := make(map[string]bool)
	for _, part := range strings.Split(raw, ",") {
		item := strings.TrimSpace(part)
		if item != "" {
			result[item] = true
		}
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

package mqtt

import (
	"context"
	"encoding/json"
	"log"
	"strings"
	"sync"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
	"robot-media-client/internal/config"
	"robot-media-client/internal/intercom"
	"robot-media-client/internal/model"
	"robot-media-client/internal/publisher"
	"robot-media-client/internal/rtsp"
)

type Client struct {
	cfg       config.Config
	probe     *rtsp.Probe
	publisher publisher.Publisher
	intercom  intercom.Manager
	mqtt      paho.Client
	mu        sync.Mutex
	// lastCmds 用于按 session 去重 MQTT 指令。服务端重试或 broker 重投时，
	// 同一个 commandId 不应再次启动推流/对讲进程。
	lastCmds    map[string]string
	stateSeq    int64
	audioVolume int
	audioMuted  bool
	deviceState map[string]map[string]any
}

func NewClient(cfg config.Config, probe *rtsp.Probe, publisher publisher.Publisher, intercomManager intercom.Manager) *Client {
	return &Client{
		cfg:         cfg,
		probe:       probe,
		publisher:   publisher,
		intercom:    intercomManager,
		lastCmds:    make(map[string]string),
		audioVolume: 50,
		audioMuted:  false,
		deviceState: make(map[string]map[string]any),
	}
}

func (c *Client) Run(ctx context.Context) error {
	// 所有控制 topic 都按 robotId 分区，确保一台机器人只消费自己的命令。
	startTopic := "robot/" + c.cfg.RobotID + "/media/video/start"
	stopTopic := "robot/" + c.cfg.RobotID + "/media/video/stop"
	switchTopic := "robot/" + c.cfg.RobotID + "/media/video/switch-channel"
	intercomStartTopic := "robot/" + c.cfg.RobotID + "/media/video/intercom/start"
	intercomStopTopic := "robot/" + c.cfg.RobotID + "/media/video/intercom/stop"
	controlTopic := "robot/" + c.cfg.RobotID + "/control/#"
	opts := paho.NewClientOptions().
		AddBroker(c.cfg.MQTTBroker).
		SetClientID(c.cfg.ClientID).
		SetAutoReconnect(true).
		SetConnectRetry(true).
		SetConnectRetryInterval(5 * time.Second).
		SetConnectTimeout(10 * time.Second).
		SetKeepAlive(20 * time.Second).
		SetPingTimeout(5 * time.Second).
		SetMaxReconnectInterval(30 * time.Second)
	if c.cfg.MQTTUsername != "" {
		opts.SetUsername(c.cfg.MQTTUsername)
		opts.SetPassword(c.cfg.MQTTPassword)
	}
	opts.SetConnectionLostHandler(func(_ paho.Client, err error) {
		log.Println("mqtt lost", err)
		// 连接丢失后立即停本地媒体进程，避免后端已经认为会话失败/重启时，
		// 旧进程仍然占用摄像头或继续推流。
		c.publisher.StopAll()
		c.intercom.StopAll()
	})
	opts.SetOnConnectHandler(func(_ paho.Client) {
		// Paho 自动重连成功后会再次触发 OnConnect，因此订阅和 online 心跳都放在这里。
		c.subscribe(startTopic, c.handleStart(ctx))
		c.subscribe(stopTopic, c.handleStop())
		c.subscribe(switchTopic, c.handleStart(ctx))
		c.subscribe(intercomStartTopic, c.handleIntercomStart(ctx))
		c.subscribe(intercomStopTopic, c.handleIntercomStop())
		c.subscribe(controlTopic, c.handleControlCommand())
		log.Println("mqtt subscribed", startTopic, stopTopic, switchTopic, intercomStartTopic, intercomStopTopic, controlTopic)
		c.online("online")
	})
	c.mqtt = paho.NewClient(opts)
	if token := c.mqtt.Connect(); token.Wait() && token.Error() != nil {
		return token.Error()
	}
	log.Println("mqtt connected", c.cfg.MQTTBroker, c.cfg.RobotID)
	heartbeat := time.NewTicker(c.cfg.HeartbeatInterval)
	defer heartbeat.Stop()
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case <-heartbeat.C:
				// 周期性 online 心跳会携带摄像头清单，后端据此刷新设备注册表。
				c.online("online")
			}
		}
	}()
	<-ctx.Done()
	// 正常退出前先停媒体，再上报 offline，最后断开 MQTT。
	c.publisher.StopAll()
	c.intercom.StopAll()
	c.online("offline")
	c.mqtt.Disconnect(250)
	return nil
}

func (c *Client) subscribe(topic string, handler paho.MessageHandler) {
	if token := c.mqtt.Subscribe(topic, 1, handler); token.Wait() && token.Error() != nil {
		log.Fatal(token.Error())
	}
}

func (c *Client) handleStart(ctx context.Context) paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var command model.StartCommand
		if err := json.Unmarshal(msg.Payload(), &command); err != nil {
			log.Println(err)
			return
		}
		if c.isDuplicate(command.SessionID, command.CommandID) {
			return
		}
		log.Println("video start", command.SessionID, command.Channel, command.Quality)
		rtspURL := command.RTSPURL
		if rtspURL == "" {
			// 服务端通常只传 deviceId/channel/quality；本地配置负责把 deviceId 映射到 RTSP URL。
			rtspURL = c.rtspURL(command.DeviceID, command.Quality)
		}
		// 先探测 RTSP，失败时马上回报 failed，避免后端一直等 track published 超时。
		if err := c.probe.Check(ctx, rtspURL); err != nil {
			c.status(command.SessionID, "failed", "", "", "RTSP_PROBE_FAILED", err.Error())
			return
		}
		c.status(command.SessionID, "publishing", "", "", "", "rtsp ok")
		// publisher.Start 会启动外部进程或默认 GStreamer pipeline，并返回前端可识别的 track 信息。
		trackSid, trackName, err := c.publisher.Start(ctx, command, rtspURL)
		if err != nil {
			c.status(command.SessionID, "failed", "", "", "PUBLISH_FAILED", err.Error())
			return
		}
		c.status(command.SessionID, "streaming", trackSid, trackName, "", "track published")
	}
}

func (c *Client) isDuplicate(sessionID, commandID string) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	if commandID != "" && commandID == c.lastCmds[sessionID] {
		return true
	}
	c.lastCmds[sessionID] = commandID
	return false
}

func (c *Client) handleStop() paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var payload model.StopCommand
		if err := json.Unmarshal(msg.Payload(), &payload); err != nil {
			log.Println(err)
			return
		}
		log.Println("video stop", payload.SessionID)
		// stop 只作用于指定 session 的推流进程，对讲音频桥由 intercom stop topic 单独控制。
		c.publisher.Stop(payload.SessionID)
		c.status(payload.SessionID, "stopped", "", "", "", "stopped")
	}
}

func (c *Client) handleIntercomStart(ctx context.Context) paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var command model.IntercomStartCommand
		if err := json.Unmarshal(msg.Payload(), &command); err != nil {
			log.Println(err)
			return
		}
		if c.isDuplicate("intercom:"+command.SessionID, command.CommandID) {
			return
		}
		log.Println("intercom start", command.SessionID, command.RoomName)
		c.intercomStatus(command.SessionID, "starting", "", "", "", "starting audio bridge")
		// intercom.Start 会同时处理两条音频链路：
		// 机器人麦克风 -> LiveKit；LiveKit 操作员麦克风 -> 本地扬声器。
		trackSid, trackName, err := c.intercom.Start(ctx, command)
		if err != nil {
			c.intercomStatus(command.SessionID, "failed", "", "", "INTERCOM_START_FAILED", err.Error())
			return
		}
		c.intercomStatus(command.SessionID, "active", trackSid, trackName, "", "intercom audio active")
	}
}

func (c *Client) handleIntercomStop() paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var payload model.StopCommand
		if err := json.Unmarshal(msg.Payload(), &payload); err != nil {
			log.Println(err)
			return
		}
		log.Println("intercom stop", payload.SessionID)
		_ = c.intercom.Stop(payload.SessionID)
		c.intercomStatus(payload.SessionID, "stopped", "", "", "", "intercom stopped")
	}
}

func (c *Client) handleControlCommand() paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var command model.ControlCommand
		if err := json.Unmarshal(msg.Payload(), &command); err != nil {
			log.Println("control command unmarshal failed", err)
			return
		}
		pretty, _ := json.MarshalIndent(command, "", "  ")
		log.Println("equipment control command received topic=", msg.Topic(), "payload=", string(pretty))
		if c.applyControlCommand(command) {
			c.online("online")
		}
	}
}

func (c *Client) applyControlCommand(command model.ControlCommand) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	changed := false
	switch command.Action {
	case "volume.set":
		c.audioVolume = clampInt(anyInt(command.Params["volume"], c.audioVolume), 0, 100)
		c.audioMuted = anyBool(command.Params["muted"], false)
		c.setDeviceStateLocked(command.Target.DeviceID, "volume", c.audioVolume)
		c.setDeviceStateLocked(command.Target.DeviceID, "muted", c.audioMuted)
		changed = true
	case "volume.up":
		step := clampInt(anyInt(command.Params["step"], 5), 1, 100)
		c.audioVolume = clampInt(anyInt(command.Params["volume"], c.audioVolume+step), 0, 100)
		c.audioMuted = anyBool(command.Params["muted"], false)
		c.setDeviceStateLocked(command.Target.DeviceID, "volume", c.audioVolume)
		c.setDeviceStateLocked(command.Target.DeviceID, "muted", c.audioMuted)
		changed = true
	case "volume.down":
		step := clampInt(anyInt(command.Params["step"], 5), 1, 100)
		c.audioVolume = clampInt(anyInt(command.Params["volume"], c.audioVolume-step), 0, 100)
		c.audioMuted = anyBool(command.Params["muted"], false)
		c.setDeviceStateLocked(command.Target.DeviceID, "volume", c.audioVolume)
		c.setDeviceStateLocked(command.Target.DeviceID, "muted", c.audioMuted)
		changed = true
	case "volume.mute":
		c.audioMuted = anyBool(command.Params["muted"], true)
		c.audioVolume = clampInt(anyInt(command.Params["volume"], c.audioVolume), 0, 100)
		c.setDeviceStateLocked(command.Target.DeviceID, "volume", c.audioVolume)
		c.setDeviceStateLocked(command.Target.DeviceID, "muted", c.audioMuted)
		changed = true
	case "payload.safety_switch":
		c.setDeviceStateLocked(command.Target.DeviceID, "safetySwitchEnabled", anyBool(command.Params["enabled"], false))
		changed = true
	case "light.warning.set":
		c.setDeviceStateLocked(command.Target.DeviceID, "enabled", anyBool(command.Params["enabled"], false))
		changed = true
	case "ptz.auto_rotate":
		c.setDeviceStateLocked(command.Target.DeviceID, "autoRotateEnabled", anyBool(command.Params["enabled"], false))
		c.setDeviceStateLocked(command.Target.DeviceID, "panSpeed", anyFloat(command.Params["panSpeed"], 0))
		changed = true
	case "light.vehicle.set":
		front, rear := vehicleLightState(command.Params)
		c.setDeviceStateLocked(command.Target.DeviceID, "front", front)
		c.setDeviceStateLocked(command.Target.DeviceID, "rear", rear)
		changed = true
	}
	return changed
}

func (c *Client) setDeviceStateLocked(deviceID string, key string, value any) {
	if deviceID == "" {
		return
	}
	status := c.deviceState[deviceID]
	if status == nil {
		status = make(map[string]any)
		c.deviceState[deviceID] = status
	}
	status[key] = value
}

func (c *Client) status(sessionID, status, trackSid, trackName, errorCode, message string) {
	c.publish("robot/"+c.cfg.RobotID+"/media/video/status", model.StatusMessage{
		SessionID: sessionID,
		Status:    status,
		TrackSid:  trackSid,
		TrackName: trackName,
		ErrorCode: errorCode,
		Message:   message,
		Timestamp: time.Now(),
	})
}

func (c *Client) intercomStatus(sessionID, status, trackSid, trackName, errorCode, message string) {
	c.publish("robot/"+c.cfg.RobotID+"/media/video/intercom/status", model.IntercomStatusMessage{
		SessionID:           sessionID,
		Status:              status,
		RobotAudioTrackSid:  trackSid,
		RobotAudioTrackName: trackName,
		ErrorCode:           errorCode,
		Message:             message,
		Timestamp:           time.Now(),
	})
}

func (c *Client) publish(topic string, payload any) {
	body, err := json.Marshal(payload)
	if err != nil {
		log.Println("mqtt publish marshal failed topic=", topic, "error=", err)
		return
	}
	if c.mqtt == nil || !c.mqtt.IsConnectionOpen() {
		return
	}
	token := c.mqtt.Publish(topic, 1, false, body)
	if !token.WaitTimeout(5 * time.Second) {
		log.Println("mqtt publish timeout topic=", topic)
		return
	}
	if err := token.Error(); err != nil {
		log.Println("mqtt publish failed topic=", topic, "error=", err)
	}
}

func (c *Client) online(status string) {
	c.mu.Lock()
	c.stateSeq++
	stateSeq := c.stateSeq
	c.mu.Unlock()
	// online/offline 消息既是心跳，也是机器人设备注册信息的来源。
	c.publish("robot/"+c.cfg.RobotID+"/media/client/status", model.OnlineMessage{
		RobotID:          c.cfg.RobotID,
		ClientID:         c.cfg.ClientID,
		Name:             c.cfg.RobotName,
		Type:             c.cfg.Type,
		Battery:          c.cfg.Battery,
		Status:           status,
		ControlMode:      "MANUAL",
		StateSeq:         stateSeq,
		MissionStatus:    "IDLE",
		NavigationStatus: "IDLE",
		ControlOwner:     nil,
		EstopActive:      false,
		Cameras:          c.cameras(),
		Devices:          c.devices(),
		Timestamp:        time.Now(),
	})
}

func (c *Client) cameras() []model.Camera {
	items := make([]model.Camera, 0, len(c.cfg.Cameras))
	for _, camera := range c.cfg.Cameras {
		items = append(items, model.Camera{
			CameraID:  camera.CameraID,
			DeviceID:  camera.DeviceID,
			GroupType: camera.GroupType,
			Name:      camera.Name,
			Quality:   camera.Quality,
		})
	}
	return items
}

func (c *Client) devices() []model.Device {
	items := make([]model.Device, 0, len(c.cfg.Devices))
	for _, device := range c.cfg.Devices {
		items = append(items, model.Device{
			DeviceID:       device.DeviceID,
			BindingID:      device.BindingID,
			Scope:          device.Scope,
			DeviceType:     device.DeviceType,
			DisplayName:    device.DisplayName,
			Vendor:         device.Vendor,
			Model:          device.Model,
			OnlineStatus:   device.OnlineStatus,
			ControlStatus:  device.ControlStatus,
			Enabled:        device.Enabled,
			RiskLevel:      device.RiskLevel,
			Actions:        append([]string(nil), device.Actions...),
			Status:         c.deviceStatus(device),
			ControlProfile: device.ControlProfile,
		})
	}
	return items
}

func (c *Client) deviceStatus(device config.Device) map[string]any {
	status := copyStringAnyMap(device.Status)
	c.mu.Lock()
	defer c.mu.Unlock()
	for key, value := range c.deviceState[device.DeviceID] {
		status[key] = value
	}
	if device.DeviceID == "audio-control-001" || device.DeviceType == "CLIENT_AUDIO" || device.DeviceType == "VOLUME_CONTROL" || device.DeviceType == "INTERCOM" {
		status["volume"] = c.audioVolume
		status["muted"] = c.audioMuted
	}
	if len(status) == 0 {
		return nil
	}
	return status
}

func (c *Client) rtspURL(deviceID string, quality string) string {
	// 优先按摄像头配置和清晰度查找，找不到时回退到兼容旧配置的 RTSP_VISIBLE_SUB。
	quality = strings.ToLower(strings.TrimSpace(quality))
	for _, camera := range c.cfg.Cameras {
		if camera.DeviceID == deviceID || camera.CameraID == deviceID {
			if quality == "main" && camera.RTSPMainURL != "" {
				return camera.RTSPMainURL
			}
			if (quality == "sub" || quality == "auto" || quality == "") && camera.RTSPSubURL != "" {
				return camera.RTSPSubURL
			}
			return camera.RTSPURL
		}
	}
	if quality == "main" && c.cfg.RTSPVisibleMain != "" {
		return c.cfg.RTSPVisibleMain
	}
	return c.cfg.RTSPVisibleSub
}

func anyInt(value any, fallback int) int {
	switch item := value.(type) {
	case float64:
		return int(item)
	case float32:
		return int(item)
	case int:
		return item
	case int64:
		return int(item)
	case int32:
		return int(item)
	default:
		return fallback
	}
}

func anyBool(value any, fallback bool) bool {
	if item, ok := value.(bool); ok {
		return item
	}
	return fallback
}

func anyFloat(value any, fallback float64) float64 {
	switch item := value.(type) {
	case float64:
		return item
	case float32:
		return float64(item)
	case int:
		return float64(item)
	case int64:
		return float64(item)
	case int32:
		return float64(item)
	default:
		return fallback
	}
}

func copyAnyMap(value any) map[string]any {
	source, ok := value.(map[string]any)
	if !ok {
		return map[string]any{}
	}
	return copyStringAnyMap(source)
}

func vehicleLightState(params map[string]any) (map[string]any, map[string]any) {
	front := copyAnyMap(params["front"])
	rear := copyAnyMap(params["rear"])
	if len(front) > 0 || len(rear) > 0 {
		return front, rear
	}
	msg := copyAnyMap(params["msg"])
	return vehicleLightPart(anyInt(msg["front_mode"], 0), anyInt(msg["front_custom_value"], 0)),
		vehicleLightPart(anyInt(msg["rear_mode"], 0), anyInt(msg["rear_custom_value"], 0))
}

func vehicleLightPart(modeCode int, customValue int) map[string]any {
	if modeCode < 0 || modeCode > 3 {
		modeCode = 0
	}
	mode := map[int]string{0: "OFF", 1: "ON", 2: "BREATH", 3: "CUSTOM"}[modeCode]
	if modeCode != 3 {
		customValue = 0
	}
	return map[string]any{
		"mode":        mode,
		"modeCode":    modeCode,
		"customValue": clampInt(customValue, 0, 100),
	}
}

func copyStringAnyMap(source map[string]any) map[string]any {
	if len(source) == 0 {
		return map[string]any{}
	}
	result := make(map[string]any, len(source))
	for key, value := range source {
		result[key] = value
	}
	return result
}

func clampInt(value, min, max int) int {
	if value < min {
		return min
	}
	if value > max {
		return max
	}
	return value
}

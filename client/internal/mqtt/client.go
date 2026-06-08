package mqtt

import (
	"context"
	"encoding/json"
	"log"
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
	lastCmds map[string]string
	stateSeq int64
}

func NewClient(cfg config.Config, probe *rtsp.Probe, publisher publisher.Publisher, intercomManager intercom.Manager) *Client {
	return &Client{cfg: cfg, probe: probe, publisher: publisher, intercom: intercomManager, lastCmds: make(map[string]string)}
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
		SetAutoReconnect(true)
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
			rtspURL = c.rtspURL(command.DeviceID)
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
	}
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
	body, _ := json.Marshal(payload)
	// QoS 1 保证状态消息至少送达一次；服务端状态处理需要具备幂等性。
	c.mqtt.Publish(topic, 1, false, body)
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
		ClientVersion:    "sim-1.0.0",
		Name:             c.cfg.RobotName,
		Type:             c.cfg.RobotType,
		Battery:          c.cfg.Battery,
		Status:           status,
		OnlineStatus:     status,
		ControlMode:      "MANUAL",
		StateSeq:         stateSeq,
		MissionStatus:    "IDLE",
		NavigationStatus: "IDLE",
		ControlOwner:     nil,
		EstopActive:      false,
		Cameras:          c.cameras(status),
		Devices:          c.statusDevices(status),
		Timestamp:        time.Now(),
	})
}

func (c *Client) cameras(status string) []model.Camera {
	items := make([]model.Camera, 0, len(c.cfg.Cameras))
	for _, camera := range c.cfg.Cameras {
		items = append(items, model.Camera{
			CameraID:  camera.CameraID,
			DeviceID:  camera.DeviceID,
			Name:      camera.Name,
			GroupType: camera.GroupType,
			Channel:   camera.Channel,
			Quality:   camera.Quality,
			Status:    status,
		})
	}
	return items
}

func (c *Client) statusDevices(status string) []model.RegistryDeviceStatus {
	onlineStatus := status
	if status == "offline" {
		onlineStatus = "offline"
	}
	devices := []model.RegistryDeviceStatus{
		{
			DeviceID:         "base",
			Scope:            "BODY",
			DeviceType:       c.baseDeviceType(),
			OnlineStatus:     onlineStatus,
			HealthStatus:     "normal",
			ControlStatus:    "idle",
			SupportedActions: []string{"drive.velocity"},
		},
		{
			DeviceID:         "ptz-dual-001",
			Scope:            "PAYLOAD",
			DeviceType:       "DUAL_LIGHT_PTZ",
			OnlineStatus:     onlineStatus,
			HealthStatus:     "normal",
			ControlStatus:    "idle",
			SupportedActions: []string{"ptz.move", "camera.zoom"},
		},
		{
			DeviceID:         "audio-control-001",
			Scope:            "AUDIO",
			DeviceType:       "CLIENT_AUDIO",
			OnlineStatus:     onlineStatus,
			HealthStatus:     "normal",
			ControlStatus:    "idle",
			SupportedActions: []string{"volume.up", "volume.down", "volume.mute"},
		},
		{
			DeviceID:         "warning-light-left",
			Scope:            "PAYLOAD",
			DeviceType:       "WARNING_LIGHT",
			OnlineStatus:     onlineStatus,
			HealthStatus:     "normal",
			ControlStatus:    "idle",
			SupportedActions: []string{"light.warning.set"},
		},
		{
			DeviceID:         "warning-light-right",
			Scope:            "PAYLOAD",
			DeviceType:       "WARNING_LIGHT",
			OnlineStatus:     onlineStatus,
			HealthStatus:     "normal",
			ControlStatus:    "idle",
			SupportedActions: []string{"light.warning.set"},
		},
		{
			DeviceID:         "vehicle-light",
			Scope:            "PAYLOAD",
			DeviceType:       "VEHICLE_LIGHT",
			OnlineStatus:     onlineStatus,
			HealthStatus:     "normal",
			ControlStatus:    "idle",
			SupportedActions: []string{"light.vehicle.set"},
		},
	}
	if c.cfg.RobotID == "robot-unitree-001" {
		devices = append(devices, model.RegistryDeviceStatus{
			DeviceID:         "searchlight-001",
			Scope:            "PAYLOAD",
			DeviceType:       "SEARCHLIGHT",
			OnlineStatus:     onlineStatus,
			HealthStatus:     "normal",
			ControlStatus:    "idle",
			SupportedActions: []string{"light.set"},
		})
	} else {
		devices = append(devices,
			model.RegistryDeviceStatus{
				DeviceID:         "launcher-001",
				Scope:            "PAYLOAD",
				DeviceType:       "LAUNCHER",
				OnlineStatus:     onlineStatus,
				HealthStatus:     "normal",
				ControlStatus:    "idle",
				SupportedActions: []string{"payload.fire"},
			},
			model.RegistryDeviceStatus{
				DeviceID:         "net-gun-001",
				Scope:            "PAYLOAD",
				DeviceType:       "NET_GUN",
				OnlineStatus:     onlineStatus,
				HealthStatus:     "normal",
				ControlStatus:    "idle",
				SupportedActions: []string{"payload.safety_switch", "payload.fire"},
			})
	}
	return devices
}

func (c *Client) baseDeviceType() string {
	if c.cfg.RobotID == "robot-songling-001" || c.cfg.RobotID == "robot-001" {
		return "WHEELED_BASE"
	}
	return "QUADRUPED_BASE"
}

func (c *Client) rtspURL(deviceID string) string {
	// 优先按摄像头配置查找，找不到时回退到兼容旧配置的 RTSP_VISIBLE_SUB。
	for _, camera := range c.cfg.Cameras {
		if camera.DeviceID == deviceID || camera.CameraID == deviceID {
			return camera.RTSPURL
		}
	}
	return c.cfg.RTSPVisibleSub
}

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
	"robot-media-client/internal/model"
	"robot-media-client/internal/publisher"
	"robot-media-client/internal/rtsp"
)

type Client struct {
	cfg       config.Config
	probe     *rtsp.Probe
	publisher publisher.Publisher
	mqtt      paho.Client
	mu        sync.Mutex
	lastCmd   string
}

func NewClient(cfg config.Config, probe *rtsp.Probe, publisher publisher.Publisher) *Client {
	return &Client{cfg: cfg, probe: probe, publisher: publisher}
}

func (c *Client) Run(ctx context.Context) error {
	startTopic := "robot/" + c.cfg.RobotID + "/media/video/start"
	stopTopic := "robot/" + c.cfg.RobotID + "/media/video/stop"
	switchTopic := "robot/" + c.cfg.RobotID + "/media/video/switch-channel"
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
		c.publisher.Stop()
	})
	opts.SetOnConnectHandler(func(_ paho.Client) {
		c.subscribe(startTopic, c.handleStart(ctx))
		c.subscribe(stopTopic, c.handleStop())
		c.subscribe(switchTopic, c.handleStart(ctx))
		log.Println("mqtt subscribed", startTopic, stopTopic, switchTopic)
		c.online("online")
	})
	c.mqtt = paho.NewClient(opts)
	if token := c.mqtt.Connect(); token.Wait() && token.Error() != nil {
		return token.Error()
	}
	log.Println("mqtt connected", c.cfg.MQTTBroker, c.cfg.RobotID)
	<-ctx.Done()
	c.publisher.Stop()
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
		if c.isDuplicate(command.CommandID) {
			return
		}
		log.Println("video start", command.SessionID, command.Channel, command.Quality)
		rtspURL := command.RTSPURL
		if rtspURL == "" {
			rtspURL = c.rtspURL(command.Channel, command.Quality)
		}
		if err := c.probe.Check(ctx, rtspURL); err != nil {
			c.status(command.SessionID, "failed", "", "", "RTSP_PROBE_FAILED", err.Error())
			return
		}
		c.status(command.SessionID, "publishing", "", "", "", "rtsp ok")
		trackSid, trackName, err := c.publisher.Start(ctx, command, rtspURL)
		if err != nil {
			c.status(command.SessionID, "failed", "", "", "PUBLISH_FAILED", err.Error())
			return
		}
		c.status(command.SessionID, "streaming", trackSid, trackName, "", "track published")
	}
}

func (c *Client) isDuplicate(commandID string) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	if commandID != "" && commandID == c.lastCmd {
		return true
	}
	c.lastCmd = commandID
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
		c.publisher.Stop()
		c.status(payload.SessionID, "stopped", "", "", "", "stopped")
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

func (c *Client) publish(topic string, payload any) {
	body, _ := json.Marshal(payload)
	c.mqtt.Publish(topic, 1, false, body)
}

func (c *Client) online(status string) {
	c.publish("robot/"+c.cfg.RobotID+"/media/client/status", model.OnlineMessage{
		RobotID:   c.cfg.RobotID,
		ClientID:  c.cfg.ClientID,
		Status:    status,
		Timestamp: time.Now(),
	})
}

func (c *Client) rtspURL(channel, quality string) string {
	key := strings.ToLower(channel) + "." + strings.ToLower(quality)
	switch key {
	case "visible.main":
		return c.cfg.RTSPVisibleMain
	case "thermal.sub":
		return c.cfg.RTSPThermalSub
	case "thermal.main":
		return c.cfg.RTSPThermalMain
	default:
		return c.cfg.RTSPVisibleSub
	}
}

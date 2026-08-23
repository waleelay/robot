package fixedcamera

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
	"robot-media-client/internal/publisher"
	"robot-media-client/internal/rtsp"
)

type Gateway struct {
	cfg       config.Config
	probe     *rtsp.Probe
	publisher publisher.Publisher
	http      *http.Client
	mqtt      paho.Client
	mu        sync.Mutex
	lastCmds  map[string]string
}

type cameraRecord struct {
	ID            string `json:"id"`
	CameraID      string `json:"cameraId"`
	CameraName    string `json:"cameraName"`
	Enabled       bool   `json:"enabled"`
	ProtocolType  string `json:"protocolType"`
	MainStreamURL string `json:"mainStreamUrl"`
	SubStreamURL  string `json:"subStreamUrl"`
}

type managementResponse struct {
	Data struct {
		Records []cameraRecord `json:"records"`
	} `json:"data"`
}

func NewGateway(cfg config.Config, probe *rtsp.Probe, pub publisher.Publisher) *Gateway {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	if cfg.ManagementInsecureTLS {
		transport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true} //nolint:gosec // 仅用于内网自签证书部署。
	}
	if strings.TrimSpace(cfg.FixedCameraGatewayID) == "" {
		cfg.FixedCameraGatewayID = "default"
	}
	return &Gateway{
		cfg:       cfg,
		probe:     probe,
		publisher: pub,
		http:      &http.Client{Timeout: 5 * time.Second, Transport: transport},
		lastCmds:  make(map[string]string),
	}
}

func (g *Gateway) Run(ctx context.Context) error {
	gatewayID := strings.TrimSpace(g.cfg.FixedCameraGatewayID)
	if gatewayID == "" {
		gatewayID = "default"
	}
	startTopic := "gateway/fixed-camera/" + gatewayID + "/video/start"
	stopTopic := "gateway/fixed-camera/" + gatewayID + "/video/stop"
	restartTopic := "gateway/fixed-camera/" + gatewayID + "/video/restart"
	opts := paho.NewClientOptions().
		AddBroker(g.cfg.MQTTBroker).
		SetClientID(g.cfg.ClientID).
		SetAutoReconnect(true).
		SetConnectRetry(true).
		SetConnectRetryInterval(5 * time.Second).
		SetConnectTimeout(10 * time.Second).
		SetKeepAlive(20 * time.Second).
		SetPingTimeout(5 * time.Second).
		SetMaxReconnectInterval(30 * time.Second)
	if g.cfg.MQTTUsername != "" {
		opts.SetUsername(g.cfg.MQTTUsername)
		opts.SetPassword(g.cfg.MQTTPassword)
	}
	opts.SetConnectionLostHandler(func(_ paho.Client, err error) {
		log.Printf("固定摄像头网关 MQTT 连接已断开：%v", err)
		g.publisher.StopAll()
	})
	opts.SetOnConnectHandler(func(_ paho.Client) {
		g.subscribe(startTopic, g.handleStart(ctx))
		g.subscribe(stopTopic, g.handleStop())
		g.subscribe(restartTopic, g.handleStart(ctx))
		log.Printf("固定摄像头网关已订阅主题，启动=%s 停止=%s 重启=%s", startTopic, stopTopic, restartTopic)
	})
	mqttClient := paho.NewClient(opts)
	g.mu.Lock()
	g.mqtt = mqttClient
	g.mu.Unlock()
	if token := mqttClient.Connect(); token.Wait() && token.Error() != nil {
		return token.Error()
	}
	log.Printf("固定摄像头网关已连接 MQTT，网关ID=%s", gatewayID)
	<-ctx.Done()
	g.publisher.StopAll()
	mqttClient.Disconnect(250)
	return nil
}

func (g *Gateway) subscribe(topic string, handler paho.MessageHandler) {
	if token := g.mqtt.Subscribe(topic, 1, handler); token.Wait() && token.Error() != nil {
		log.Fatalf("订阅固定摄像头 MQTT 主题失败，主题=%s：%v", topic, token.Error())
	}
}

func (g *Gateway) handleStart(ctx context.Context) paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var command model.StartCommand
		if err := json.Unmarshal(msg.Payload(), &command); err != nil {
			log.Printf("解析固定摄像头启动命令失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		if g.isDuplicate(command.SessionID, command.CommandID) {
			return
		}
		cameraID := firstNonBlank(command.SourceID, command.DeviceID, command.RobotID)
		log.Printf("开始固定摄像头推流，摄像头ID=%s 会话ID=%s 清晰度=%s", cameraID, command.SessionID, command.Quality)
		rtspURL := strings.TrimSpace(command.RTSPURL)
		if rtspURL == "" {
			var err error
			rtspURL, err = g.rtspURL(ctx, cameraID, command.Quality)
			if err != nil {
				g.status(command.SessionID, "failed", "", "", "FIXED_CAMERA_CONFIG_FAILED", err.Error())
				return
			}
		}
		if _, err := g.probe.Check(ctx, rtspURL); err != nil {
			g.status(command.SessionID, "failed", "", "", "RTSP_PROBE_FAILED", err.Error())
			return
		}
		g.status(command.SessionID, "publishing", "", "", "", "RTSP 探测成功")
		trackSid, trackName, err := g.publisher.Start(ctx, command, rtspURL)
		if err != nil {
			g.status(command.SessionID, "failed", "", "", "PUBLISH_FAILED", err.Error())
			return
		}
		g.status(command.SessionID, "streaming", trackSid, trackName, "", "视频轨道发布成功")
	}
}

func (g *Gateway) handleStop() paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var payload model.StopCommand
		if err := json.Unmarshal(msg.Payload(), &payload); err != nil {
			log.Printf("解析固定摄像头停止命令失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		log.Printf("停止固定摄像头推流，摄像头ID=%s 会话ID=%s", payload.SourceID, payload.SessionID)
		if err := g.publisher.StopStream(payload); err != nil {
			log.Printf("停止固定摄像头推流进程失败，摄像头ID=%s 会话ID=%s：%v", payload.SourceID, payload.SessionID, err)
		}
		g.status(payload.SessionID, "stopped", "", "", "", "推流已停止")
	}
}

func (g *Gateway) rtspURL(ctx context.Context, cameraID string, quality string) (string, error) {
	camera, err := g.camera(ctx, cameraID)
	if err != nil {
		return "", err
	}
	if !camera.Enabled {
		return "", fmt.Errorf("固定摄像头未启用：%s", cameraID)
	}
	if !strings.EqualFold(camera.ProtocolType, "") && !strings.EqualFold(camera.ProtocolType, "RTSP") {
		return "", fmt.Errorf("不支持的固定摄像头协议：%s", camera.ProtocolType)
	}
	quality = strings.ToLower(strings.TrimSpace(quality))
	if quality == "main" && strings.TrimSpace(camera.MainStreamURL) != "" {
		return camera.MainStreamURL, nil
	}
	if (quality == "sub" || quality == "auto" || quality == "") && strings.TrimSpace(camera.SubStreamURL) != "" {
		return camera.SubStreamURL, nil
	}
	if strings.TrimSpace(camera.MainStreamURL) != "" {
		return camera.MainStreamURL, nil
	}
	if strings.TrimSpace(camera.SubStreamURL) != "" {
		return camera.SubStreamURL, nil
	}
	return "", fmt.Errorf("固定摄像头未配置码流地址：%s", cameraID)
}

func (g *Gateway) camera(ctx context.Context, cameraID string) (cameraRecord, error) {
	if strings.TrimSpace(cameraID) == "" {
		return cameraRecord{}, fmt.Errorf("固定摄像头 ID 不能为空")
	}
	baseURL := strings.TrimRight(g.cfg.ManagementServiceURL, "/")
	requestURL, err := url.Parse(baseURL + "/api/v1/management/fixed-cameras")
	if err != nil {
		return cameraRecord{}, err
	}
	values := requestURL.Query()
	values.Set("pageNum", "1")
	values.Set("pageSize", "500")
	requestURL.RawQuery = values.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL.String(), nil)
	if err != nil {
		return cameraRecord{}, err
	}
	if g.cfg.ManagementToken != "" {
		req.Header.Set("Authorization", "Bearer "+g.cfg.ManagementToken)
	}
	resp, err := g.http.Do(req)
	if err != nil {
		return cameraRecord{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return cameraRecord{}, fmt.Errorf("查询管理端固定摄像头失败，状态码=%d", resp.StatusCode)
	}
	var body managementResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return cameraRecord{}, err
	}
	for _, camera := range body.Data.Records {
		if cameraID == firstNonBlank(camera.CameraID, camera.ID) {
			return camera, nil
		}
	}
	return cameraRecord{}, fmt.Errorf("未找到固定摄像头：%s", cameraID)
}

func (g *Gateway) status(sessionID, status, trackSid, trackName, errorCode, message string) {
	g.publish("gateway/fixed-camera/"+g.cfg.FixedCameraGatewayID+"/video/status", model.StatusMessage{
		SessionID: sessionID,
		Status:    status,
		TrackSid:  trackSid,
		TrackName: trackName,
		ErrorCode: errorCode,
		Message:   message,
		Timestamp: time.Now(),
	})
}

func (g *Gateway) publish(topic string, payload any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		log.Printf("序列化固定摄像头 MQTT 状态失败，主题=%s：%v", topic, err)
		return err
	}
	g.mu.Lock()
	mqttClient := g.mqtt
	g.mu.Unlock()
	if mqttClient == nil || !mqttClient.IsConnectionOpen() {
		return fmt.Errorf("MQTT 尚未连接")
	}
	token := mqttClient.Publish(topic, 1, false, body)
	if !token.WaitTimeout(5 * time.Second) {
		return fmt.Errorf("MQTT 状态发布超时，主题=%s", topic)
	}
	return token.Error()
}

func (g *Gateway) isDuplicate(sessionID, commandID string) bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	if commandID != "" && commandID == g.lastCmds[sessionID] {
		return true
	}
	g.lastCmds[sessionID] = commandID
	return false
}

func firstNonBlank(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return value
		}
	}
	return ""
}

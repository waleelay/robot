package fixedcamera

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"fixed-camera-gateway/internal/config"
	"fixed-camera-gateway/internal/model"
	"fixed-camera-gateway/internal/publisher"
	"fixed-camera-gateway/internal/rtsp"
	paho "github.com/eclipse/paho.mqtt.golang"
)

type Gateway struct {
	cfg                config.Config
	probe              streamProber
	publisher          publisher.Publisher
	mqtt               paho.Client
	mu                 sync.Mutex
	lastCmds           map[string]string
	activeSessions     map[string]struct{}
	recoveryAttempts   map[string]int
	sequence           atomic.Uint64
	probeRunning       atomic.Bool
	catalogMu          sync.Mutex
	catalog            map[string]leasedCamera
	lastCatalog        time.Time
	lastCatalogVersion uint64
}

const maxAutomaticRecoveryAttempts = 8

type streamProber interface {
	Check(context.Context, string) (rtsp.StreamInfo, error)
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

type leasedCamera struct {
	record    cameraRecord
	expiresAt time.Time
}

func NewGateway(cfg config.Config, probe streamProber, pub publisher.Publisher) *Gateway {
	if strings.TrimSpace(cfg.GatewayID) == "" {
		cfg.GatewayID = "default"
	}
	return &Gateway{
		cfg:              cfg,
		probe:            probe,
		publisher:        pub,
		lastCmds:         make(map[string]string),
		activeSessions:   make(map[string]struct{}),
		recoveryAttempts: make(map[string]int),
		catalog:          make(map[string]leasedCamera),
	}
}

func (g *Gateway) Run(ctx context.Context) error {
	gatewayID := strings.TrimSpace(g.cfg.GatewayID)
	if gatewayID == "" {
		gatewayID = "default"
	}
	startTopic := "gateway/fixed-camera/" + gatewayID + "/video/start"
	stopTopic := "gateway/fixed-camera/" + gatewayID + "/video/stop"
	restartTopic := "gateway/fixed-camera/" + gatewayID + "/video/restart"
	catalogTopic := "gateway/fixed-camera/" + gatewayID + "/catalog/sync"
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
	offlinePayload, _ := json.Marshal(model.FixedCameraGatewayStatus{
		Version: "1.0", GatewayID: gatewayID, Status: "OFFLINE", ReasonCode: "MQTT_CONNECTION_LOST",
	})
	opts.SetWill("gateway/fixed-camera/"+gatewayID+"/status", string(offlinePayload), 1, false)
	opts.SetOnConnectHandler(func(_ paho.Client) {
		g.subscribe(startTopic, g.handleStart(ctx))
		g.subscribe(stopTopic, g.handleStop())
		g.subscribe(restartTopic, g.handleStart(ctx))
		g.subscribe(catalogTopic, g.handleCatalog(ctx))
		log.Printf("固定摄像头网关已订阅主题，启动=%s 停止=%s 重启=%s", startTopic, stopTopic, restartTopic)
		g.publishGatewayStatus("ONLINE", "")
	})
	mqttClient := paho.NewClient(opts)
	g.mu.Lock()
	g.mqtt = mqttClient
	g.mu.Unlock()
	if token := mqttClient.Connect(); token.Wait() && token.Error() != nil {
		return token.Error()
	}
	log.Printf("固定摄像头网关已连接 MQTT，网关ID=%s", gatewayID)
	heartbeatInterval := g.cfg.HeartbeatInterval
	if heartbeatInterval <= 0 {
		heartbeatInterval = 10 * time.Second
	}
	probeInterval := g.cfg.HealthProbeInterval
	if probeInterval <= 0 {
		probeInterval = 60 * time.Second
	}
	heartbeatTicker := time.NewTicker(heartbeatInterval)
	probeTicker := time.NewTicker(probeInterval)
	defer heartbeatTicker.Stop()
	defer probeTicker.Stop()
	go g.probeAllCameras(ctx)
	for {
		select {
		case <-ctx.Done():
			g.stopAllPublishers("网关进程停止")
			g.publishGatewayStatus("OFFLINE", "PROCESS_STOPPED")
			mqttClient.Disconnect(250)
			return nil
		case <-heartbeatTicker.C:
			g.publishGatewayStatus("ONLINE", "")
		case <-probeTicker.C:
			go g.probeAllCameras(ctx)
		}
	}
}

func (g *Gateway) ObservePublisherEvents(ctx context.Context, events <-chan publisher.LifecycleEvent) {
	for {
		select {
		case <-ctx.Done():
			return
		case event := <-events:
			for _, sessionID := range event.SessionIDs {
				g.forgetSession(sessionID)
				if event.ReasonCode == "PUBLISH_PROCESS_EXITED" {
					g.mu.Lock()
					g.recoveryAttempts[sessionID] = 0
					g.mu.Unlock()
				}
				g.status(sessionID, publisherEventStatus(event.ReasonCode), "", "", event.ReasonCode, event.Message)
			}
		}
	}
}

// 推流进程意外退出通常由短暂 RTSP 断流引起，保留 viewer 后应由服务端调度重发启动命令。
// 其他不可恢复原因仍按 failed 收口，避免无限重试错误配置或显式停止的会话。
func publisherEventStatus(reasonCode string) string {
	if reasonCode == "PUBLISH_PROCESS_EXITED" {
		return "interrupted"
	}
	return "failed"
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
		cameraID := firstNonBlank(command.SourceID, command.DeviceID)
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
			g.publishCameraHealth(model.FixedCameraHealthStatus{
				Version: "1.0", GatewayID: g.cfg.GatewayID, CameraID: cameraID,
				Health: "UNAVAILABLE", Sequence: g.sequence.Add(1), CheckedAt: time.Now(),
				ReasonCode: "RTSP_PROBE_FAILED",
			})
			g.status(command.SessionID, g.recoveryProbeStatus(command.SessionID), "", "", "RTSP_PROBE_FAILED", err.Error())
			return
		}
		g.clearRecovery(command.SessionID)
		g.publishCameraHealth(model.FixedCameraHealthStatus{
			Version: "1.0", GatewayID: g.cfg.GatewayID, CameraID: cameraID,
			Health: "AVAILABLE", Sequence: g.sequence.Add(1), CheckedAt: time.Now(),
		})
		g.status(command.SessionID, "publishing", "", "", "", "RTSP 探测成功")
		_, trackName, err := g.publisher.Start(ctx, command, rtspURL)
		if err != nil {
			g.status(command.SessionID, "failed", "", "", "PUBLISH_FAILED", err.Error())
			return
		}
		g.rememberSession(command.SessionID)
		// gstreamer-publisher 无法返回 LiveKit 的真实 Track SID，不能把本地占位符当作轨道存在依据。
		// Media Service 会通过 Room API 校验真实轨道，避免网关重启后复用已失效的会话。
		g.status(command.SessionID, "streaming", "", trackName, "", "视频轨道发布成功")
	}
}

func (g *Gateway) recoveryProbeStatus(sessionID string) string {
	g.mu.Lock()
	defer g.mu.Unlock()
	attempts, recovering := g.recoveryAttempts[sessionID]
	if !recovering {
		return "failed"
	}
	attempts++
	if attempts > maxAutomaticRecoveryAttempts {
		delete(g.recoveryAttempts, sessionID)
		return "failed"
	}
	g.recoveryAttempts[sessionID] = attempts
	return "interrupted"
}

func (g *Gateway) clearRecovery(sessionID string) {
	g.mu.Lock()
	delete(g.recoveryAttempts, sessionID)
	g.mu.Unlock()
}

func (g *Gateway) handleStop() paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var payload model.StopCommand
		if err := json.Unmarshal(msg.Payload(), &payload); err != nil {
			log.Printf("解析固定摄像头停止命令失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		g.stop(payload)
	}
}

func (g *Gateway) stop(payload model.StopCommand) {
	log.Printf("停止固定摄像头推流，摄像头ID=%s 会话ID=%s", payload.SourceID, payload.SessionID)
	if err := g.publisher.Stop(payload.SessionID); err != nil {
		log.Printf("停止固定摄像头推流进程失败，摄像头ID=%s 会话ID=%s：%v", payload.SourceID, payload.SessionID, err)
	}
	g.forgetSession(payload.SessionID)
	g.status(payload.SessionID, "stopped", "", "", "", "推流已停止")
}

// stopAllPublishers 在 MQTT 仍连接时先收口会话，避免留下没有实际推流的 STREAMING 状态。
func (g *Gateway) stopAllPublishers(message string) {
	sessionIDs := g.activeSessionIDs()
	if err := g.publisher.StopAll(); err != nil {
		log.Printf("停止全部固定摄像头推流进程失败：%v", err)
	}
	for _, sessionID := range sessionIDs {
		g.status(sessionID, "stopped", "", "", "GATEWAY_STOPPED", message)
		g.forgetSession(sessionID)
	}
}

func (g *Gateway) rememberSession(sessionID string) {
	if strings.TrimSpace(sessionID) == "" {
		return
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	g.activeSessions[sessionID] = struct{}{}
}

func (g *Gateway) forgetSession(sessionID string) {
	g.mu.Lock()
	defer g.mu.Unlock()
	delete(g.activeSessions, sessionID)
}

func (g *Gateway) activeSessionIDs() []string {
	g.mu.Lock()
	defer g.mu.Unlock()
	result := make([]string, 0, len(g.activeSessions))
	for sessionID := range g.activeSessions {
		result = append(result, sessionID)
	}
	return result
}

func (g *Gateway) handleCatalog(ctx context.Context) paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var snapshot model.FixedCameraCatalogSnapshot
		if err := json.Unmarshal(msg.Payload(), &snapshot); err != nil {
			log.Printf("解析固定摄像头目录快照失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		if err := g.applyCatalog(snapshot, time.Now()); err != nil {
			log.Printf("拒绝固定摄像头目录快照，网关ID=%s：%v", g.cfg.GatewayID, err)
			return
		}
		log.Printf("固定摄像头目录快照已更新，网关ID=%s 摄像头数=%d", snapshot.GatewayID, len(snapshot.Cameras))
		// 目录首次下发或发生变更后立即刷新健康状态，避免等待下一轮定时探测。
		go g.probeAllCameras(ctx)
	}
}

func (g *Gateway) applyCatalog(snapshot model.FixedCameraCatalogSnapshot, now time.Time) error {
	if strings.TrimSpace(snapshot.GatewayID) != strings.TrimSpace(g.cfg.GatewayID) {
		return fmt.Errorf("快照网关身份不匹配")
	}
	if snapshot.CatalogVersion == 0 || snapshot.IssuedAt.IsZero() || snapshot.IssuedAt.After(now.Add(30*time.Second)) {
		return fmt.Errorf("快照签发时间无效")
	}
	g.catalogMu.Lock()
	defer g.catalogMu.Unlock()
	if !g.lastCatalog.IsZero() && snapshot.IssuedAt.Before(g.lastCatalog) {
		return fmt.Errorf("快照时间早于当前版本")
	}
	if snapshot.IssuedAt.Equal(g.lastCatalog) && snapshot.CatalogVersion <= g.lastCatalogVersion {
		return fmt.Errorf("快照版本未递增")
	}
	next := make(map[string]leasedCamera)
	for _, source := range snapshot.Cameras {
		cameraID := strings.TrimSpace(source.CameraID)
		if cameraID == "" || !source.ExpiresAt.After(now) {
			continue
		}
		next[cameraID] = leasedCamera{
			record: cameraRecord{
				CameraID: cameraID, Enabled: source.Enabled, ProtocolType: source.ProtocolType,
				MainStreamURL: source.MainStreamURL, SubStreamURL: source.SubStreamURL,
			},
			expiresAt: source.ExpiresAt,
		}
	}
	g.catalog = next
	g.lastCatalog = snapshot.IssuedAt
	g.lastCatalogVersion = snapshot.CatalogVersion
	return nil
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

func rtspURLForCamera(camera cameraRecord, quality string) (string, string) {
	if !camera.Enabled {
		return "", "CONFIG_DISABLED"
	}
	if camera.ProtocolType != "" && !strings.EqualFold(camera.ProtocolType, "RTSP") {
		return "", "PROTOCOL_UNSUPPORTED"
	}
	if (quality == "sub" || quality == "auto" || quality == "") && strings.TrimSpace(camera.SubStreamURL) != "" {
		return camera.SubStreamURL, ""
	}
	if strings.TrimSpace(camera.MainStreamURL) != "" {
		return camera.MainStreamURL, ""
	}
	if strings.TrimSpace(camera.SubStreamURL) != "" {
		return camera.SubStreamURL, ""
	}
	return "", "STREAM_NOT_CONFIGURED"
}

func (g *Gateway) cameras() []cameraRecord {
	return g.leasedCameras(time.Now())
}

func (g *Gateway) leasedCameras(now time.Time) []cameraRecord {
	g.catalogMu.Lock()
	defer g.catalogMu.Unlock()
	result := make([]cameraRecord, 0, len(g.catalog))
	for cameraID, camera := range g.catalog {
		if !camera.expiresAt.After(now) {
			delete(g.catalog, cameraID)
			continue
		}
		result = append(result, camera.record)
	}
	return result
}

func (g *Gateway) probeAllCameras(ctx context.Context) {
	if !g.probeRunning.CompareAndSwap(false, true) {
		log.Printf("固定摄像头健康检查仍在执行，本轮跳过")
		return
	}
	defer g.probeRunning.Store(false)
	cameras := g.cameras()
	workers := g.cfg.HealthProbeWorkers
	if workers < 1 {
		workers = 1
	}
	semaphore := make(chan struct{}, workers)
	var wait sync.WaitGroup
	for _, camera := range cameras {
		camera := camera
		wait.Add(1)
		go func() {
			defer wait.Done()
			select {
			case semaphore <- struct{}{}:
				defer func() { <-semaphore }()
			case <-ctx.Done():
				return
			}
			g.publishCameraHealth(g.cameraHealth(ctx, camera))
		}()
	}
	wait.Wait()
}

func (g *Gateway) cameraHealth(ctx context.Context, camera cameraRecord) model.FixedCameraHealthStatus {
	cameraID := firstNonBlank(camera.CameraID, camera.ID)
	result := model.FixedCameraHealthStatus{
		Version: "1.0", GatewayID: g.cfg.GatewayID, CameraID: cameraID,
		Health: "UNKNOWN", Sequence: g.sequence.Add(1), CheckedAt: time.Now(),
	}
	rtspURL, reasonCode := rtspURLForCamera(camera, "sub")
	if reasonCode != "" {
		result.ReasonCode = reasonCode
		return result
	}
	if _, err := g.probe.Check(ctx, rtspURL); err != nil {
		result.Health = "UNAVAILABLE"
		result.ReasonCode = "RTSP_PROBE_FAILED"
		return result
	}
	result.Health = "AVAILABLE"
	return result
}

func (g *Gateway) publishGatewayStatus(status, reasonCode string) {
	if err := g.publish("gateway/fixed-camera/"+g.cfg.GatewayID+"/status", model.FixedCameraGatewayStatus{
		Version: "1.0", GatewayID: g.cfg.GatewayID, Status: status,
		Sequence: g.sequence.Add(1), ReportedAt: time.Now(), ReasonCode: reasonCode,
	}); err != nil {
		log.Printf("发布固定摄像头网关状态失败，网关ID=%s 状态=%s：%v", g.cfg.GatewayID, status, err)
	}
}

func (g *Gateway) publishCameraHealth(status model.FixedCameraHealthStatus) {
	if strings.TrimSpace(status.CameraID) == "" {
		return
	}
	if err := g.publish("gateway/fixed-camera/"+g.cfg.GatewayID+"/camera/"+status.CameraID+"/status", status); err != nil {
		log.Printf("发布固定摄像头健康状态失败，网关ID=%s 摄像头ID=%s 状态=%s：%v",
			g.cfg.GatewayID, status.CameraID, status.Health, err)
	}
}

func (g *Gateway) camera(ctx context.Context, cameraID string) (cameraRecord, error) {
	if strings.TrimSpace(cameraID) == "" {
		return cameraRecord{}, fmt.Errorf("固定摄像头 ID 不能为空")
	}
	cameras := g.cameras()
	for _, camera := range cameras {
		if cameraID == firstNonBlank(camera.CameraID, camera.ID) {
			return camera, nil
		}
	}
	return cameraRecord{}, fmt.Errorf("未找到固定摄像头：%s", cameraID)
}

func (g *Gateway) status(sessionID, status, trackSid, trackName, errorCode, message string) {
	if err := g.publish("gateway/fixed-camera/"+g.cfg.GatewayID+"/video/status", model.StatusMessage{
		SessionID: sessionID,
		Status:    status,
		TrackSid:  trackSid,
		TrackName: trackName,
		ErrorCode: errorCode,
		Message:   message,
		Timestamp: time.Now(),
	}); err != nil {
		log.Printf("发布固定摄像头视频状态失败，会话ID=%s 状态=%s：%v", sessionID, status, err)
	}
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

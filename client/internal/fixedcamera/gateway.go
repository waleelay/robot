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
	"sync/atomic"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
	"robot-media-client/internal/publisher"
	"robot-media-client/internal/rtsp"
)

type Gateway struct {
	cfg                config.Config
	probe              streamProber
	publisher          publisher.Publisher
	http               *http.Client
	mqtt               paho.Client
	mu                 sync.Mutex
	lastCmds           map[string]string
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

type managementResponse struct {
	Data struct {
		Records []cameraRecord `json:"records"`
		Total   int64          `json:"total"`
	} `json:"data"`
}

type leasedCamera struct {
	record    cameraRecord
	expiresAt time.Time
}

func NewGateway(cfg config.Config, probe streamProber, pub publisher.Publisher) *Gateway {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	if cfg.ManagementInsecureTLS {
		transport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true} //nolint:gosec // 仅用于内网自签证书部署。
	}
	if strings.TrimSpace(cfg.FixedCameraGatewayID) == "" {
		cfg.FixedCameraGatewayID = "default"
	}
	return &Gateway{
		cfg:              cfg,
		probe:            probe,
		publisher:        pub,
		http:             &http.Client{Timeout: 5 * time.Second, Transport: transport},
		lastCmds:         make(map[string]string),
		recoveryAttempts: make(map[string]int),
		catalog:          make(map[string]leasedCamera),
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
		if g.catalogMode() != "management" {
			g.subscribe(catalogTopic, g.handleCatalog())
		}
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
	heartbeatInterval := g.cfg.FixedCameraHeartbeat
	if heartbeatInterval <= 0 {
		heartbeatInterval = 10 * time.Second
	}
	probeInterval := g.cfg.FixedCameraHealthProbe
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
			g.publishGatewayStatus("OFFLINE", "PROCESS_STOPPED")
			g.publisher.StopAll()
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
			g.publishCameraHealth(model.FixedCameraHealthStatus{
				Version: "1.0", GatewayID: g.cfg.FixedCameraGatewayID, CameraID: cameraID,
				Health: "UNAVAILABLE", Sequence: g.sequence.Add(1), CheckedAt: time.Now(),
				ReasonCode: "RTSP_PROBE_FAILED",
			})
			g.status(command.SessionID, g.recoveryProbeStatus(command.SessionID), "", "", "RTSP_PROBE_FAILED", err.Error())
			return
		}
		g.clearRecovery(command.SessionID)
		g.publishCameraHealth(model.FixedCameraHealthStatus{
			Version: "1.0", GatewayID: g.cfg.FixedCameraGatewayID, CameraID: cameraID,
			Health: "AVAILABLE", Sequence: g.sequence.Add(1), CheckedAt: time.Now(),
		})
		g.status(command.SessionID, "publishing", "", "", "", "RTSP 探测成功")
		trackSid, trackName, err := g.publisher.Start(ctx, command, rtspURL)
		if err != nil {
			g.status(command.SessionID, "failed", "", "", "PUBLISH_FAILED", err.Error())
			return
		}
		g.status(command.SessionID, "streaming", trackSid, trackName, "", "视频轨道发布成功")
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
	g.status(payload.SessionID, "stopped", "", "", "", "推流已停止")
}

func (g *Gateway) handleCatalog() paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var snapshot model.FixedCameraCatalogSnapshot
		if err := json.Unmarshal(msg.Payload(), &snapshot); err != nil {
			log.Printf("解析固定摄像头目录快照失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		if err := g.applyCatalog(snapshot, time.Now()); err != nil {
			log.Printf("拒绝固定摄像头目录快照，网关ID=%s：%v", g.cfg.FixedCameraGatewayID, err)
			return
		}
		log.Printf("固定摄像头目录快照已更新，网关ID=%s 摄像头数=%d", snapshot.GatewayID, len(snapshot.Cameras))
	}
}

func (g *Gateway) applyCatalog(snapshot model.FixedCameraCatalogSnapshot, now time.Time) error {
	if strings.TrimSpace(snapshot.GatewayID) != strings.TrimSpace(g.cfg.FixedCameraGatewayID) {
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

func (g *Gateway) cameras(ctx context.Context) ([]cameraRecord, error) {
	if g.catalogMode() != "management" {
		return g.leasedCameras(time.Now()), nil
	}
	return g.managementCameras(ctx)
}

func (g *Gateway) managementCameras(ctx context.Context) ([]cameraRecord, error) {
	const pageSize = 500
	const maxPages = 1000
	var cameras []cameraRecord
	for pageNum := 1; pageNum <= maxPages; pageNum++ {
		page, total, err := g.cameraPage(ctx, pageNum, pageSize)
		if err != nil {
			return nil, err
		}
		cameras = append(cameras, page...)
		if total > 0 && int64(len(cameras)) >= total {
			return cameras, nil
		}
		if total <= 0 && len(page) < pageSize {
			return cameras, nil
		}
		if len(page) == 0 {
			return nil, fmt.Errorf("固定摄像头分页提前结束，已读取=%d 总数=%d", len(cameras), total)
		}
	}
	return nil, fmt.Errorf("固定摄像头分页数量超过安全上限，页数=%d", maxPages)
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

func (g *Gateway) catalogMode() string {
	mode := strings.ToLower(strings.TrimSpace(g.cfg.FixedCameraCatalogMode))
	if mode == "management" {
		return mode
	}
	return "lease"
}

func (g *Gateway) cameraPage(ctx context.Context, pageNum, pageSize int) ([]cameraRecord, int64, error) {
	baseURL := strings.TrimRight(g.cfg.ManagementServiceURL, "/")
	requestURL, err := url.Parse(baseURL + "/api/v1/management/fixed-cameras")
	if err != nil {
		return nil, 0, err
	}
	values := requestURL.Query()
	values.Set("pageNum", fmt.Sprintf("%d", pageNum))
	values.Set("pageSize", fmt.Sprintf("%d", pageSize))
	requestURL.RawQuery = values.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL.String(), nil)
	if err != nil {
		return nil, 0, err
	}
	if g.cfg.ManagementToken != "" {
		req.Header.Set("Authorization", "Bearer "+g.cfg.ManagementToken)
	}
	resp, err := g.http.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, 0, fmt.Errorf("查询管理端固定摄像头失败，状态码=%d", resp.StatusCode)
	}
	var body managementResponse
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, 0, err
	}
	return body.Data.Records, body.Data.Total, nil
}

func (g *Gateway) probeAllCameras(ctx context.Context) {
	if !g.probeRunning.CompareAndSwap(false, true) {
		log.Printf("固定摄像头健康检查仍在执行，本轮跳过")
		return
	}
	defer g.probeRunning.Store(false)
	cameras, err := g.cameras(ctx)
	if err != nil {
		log.Printf("固定摄像头健康检查读取档案失败：%v", err)
		return
	}
	workers := g.cfg.FixedCameraProbeWorkers
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
		Version: "1.0", GatewayID: g.cfg.FixedCameraGatewayID, CameraID: cameraID,
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
	if err := g.publish("gateway/fixed-camera/"+g.cfg.FixedCameraGatewayID+"/status", model.FixedCameraGatewayStatus{
		Version: "1.0", GatewayID: g.cfg.FixedCameraGatewayID, Status: status,
		Sequence: g.sequence.Add(1), ReportedAt: time.Now(), ReasonCode: reasonCode,
	}); err != nil {
		log.Printf("发布固定摄像头网关状态失败，网关ID=%s 状态=%s：%v", g.cfg.FixedCameraGatewayID, status, err)
	}
}

func (g *Gateway) publishCameraHealth(status model.FixedCameraHealthStatus) {
	if strings.TrimSpace(status.CameraID) == "" {
		return
	}
	if err := g.publish("gateway/fixed-camera/"+g.cfg.FixedCameraGatewayID+"/camera/"+status.CameraID+"/status", status); err != nil {
		log.Printf("发布固定摄像头健康状态失败，网关ID=%s 摄像头ID=%s 状态=%s：%v",
			g.cfg.FixedCameraGatewayID, status.CameraID, status.Health, err)
	}
}

func (g *Gateway) camera(ctx context.Context, cameraID string) (cameraRecord, error) {
	if strings.TrimSpace(cameraID) == "" {
		return cameraRecord{}, fmt.Errorf("固定摄像头 ID 不能为空")
	}
	cameras, err := g.cameras(ctx)
	if err != nil {
		return cameraRecord{}, err
	}
	for _, camera := range cameras {
		if cameraID == firstNonBlank(camera.CameraID, camera.ID) {
			return camera, nil
		}
	}
	return cameraRecord{}, fmt.Errorf("未找到固定摄像头：%s", cameraID)
}

func (g *Gateway) status(sessionID, status, trackSid, trackName, errorCode, message string) {
	if err := g.publish("gateway/fixed-camera/"+g.cfg.FixedCameraGatewayID+"/video/status", model.StatusMessage{
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

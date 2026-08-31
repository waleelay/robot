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
	startingSessions   map[string]int
	stoppingSessions   map[string]int
	pendingStatuses    map[string]pendingSessionStatus
	dispatcher         *commandDispatcher
	connectionCancel   context.CancelFunc
	subscriptionsReady atomic.Bool
	sequence           atomic.Uint64
	probeRunning       atomic.Bool
	catalogMu          sync.Mutex
	catalog            map[string]leasedCamera
	lastCatalog        time.Time
	lastCatalogVersion uint64
}

type pendingSessionStatus struct {
	status    string
	errorCode string
	message   string
}

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
		startingSessions: make(map[string]int),
		stoppingSessions: make(map[string]int),
		pendingStatuses:  make(map[string]pendingSessionStatus),
		catalog:          make(map[string]leasedCamera),
	}
}

func (g *Gateway) Run(ctx context.Context) error {
	g.dispatcher = newCommandDispatcher(ctx)
	defer g.dispatcher.close()
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
		g.handleConnectionLost()
	})
	offlinePayload, _ := json.Marshal(model.FixedCameraGatewayStatus{
		Version: "1.0", GatewayID: gatewayID, Status: "OFFLINE", ReasonCode: "MQTT_CONNECTION_LOST",
	})
	opts.SetWill("gateway/fixed-camera/"+gatewayID+"/status", string(offlinePayload), 1, false)
	opts.SetOnConnectHandler(func(_ paho.Client) {
		connectionCtx := g.beginConnection(ctx)
		go g.restoreSubscriptions(connectionCtx, startTopic, stopTopic, restartTopic, catalogTopic)
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
			if g.subscriptionsReady.Load() {
				g.flushPendingStatuses()
				g.publishGatewayStatus("ONLINE", "")
			}
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
				g.status(sessionID, "interrupted", "", "", event.ReasonCode, event.Message)
			}
		}
	}
}

func (g *Gateway) subscribe(topic string, handler paho.MessageHandler) error {
	token := g.mqtt.Subscribe(topic, 1, handler)
	if !token.WaitTimeout(5 * time.Second) {
		return fmt.Errorf("订阅主题 %s 超时", topic)
	}
	if token.Error() != nil {
		return fmt.Errorf("订阅主题 %s 失败：%w", topic, token.Error())
	}
	return nil
}

func (g *Gateway) beginConnection(parent context.Context) context.Context {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.connectionCancel != nil {
		g.connectionCancel()
	}
	connectionCtx, cancel := context.WithCancel(parent)
	g.connectionCancel = cancel
	g.subscriptionsReady.Store(false)
	return connectionCtx
}

func (g *Gateway) restoreSubscriptions(
	ctx context.Context,
	startTopic, stopTopic, restartTopic, catalogTopic string,
) {
	for {
		err := g.subscribe(startTopic, g.handleStart(ctx, normalCommand))
		if err == nil {
			err = g.subscribe(stopTopic, g.handleStop(ctx))
		}
		if err == nil {
			err = g.subscribe(restartTopic, g.handleStart(ctx, priorityCommand))
		}
		if err == nil {
			err = g.subscribe(catalogTopic, g.handleCatalog(ctx))
		}
		if err == nil {
			if ctx.Err() != nil {
				return
			}
			g.subscriptionsReady.Store(true)
			log.Printf("固定摄像头网关 MQTT 主题订阅已恢复")
			g.flushPendingStatuses()
			g.publishGatewayStatus("ONLINE", "")
			return
		}
		log.Printf("恢复固定摄像头 MQTT 订阅失败，5 秒后重试：%v", err)
		select {
		case <-ctx.Done():
			return
		case <-time.After(5 * time.Second):
		}
	}
}

func (g *Gateway) handleConnectionLost() {
	g.subscriptionsReady.Store(false)
	g.mu.Lock()
	if g.connectionCancel != nil {
		g.connectionCancel()
		g.connectionCancel = nil
	}
	for sessionID := range g.activeSessions {
		g.pendingStatuses[sessionID] = pendingSessionStatus{
			status: "interrupted", errorCode: "MQTT_CONNECTION_LOST", message: "MQTT 连接中断",
		}
	}
	for sessionID := range g.startingSessions {
		g.pendingStatuses[sessionID] = pendingSessionStatus{
			status: "interrupted", errorCode: "MQTT_CONNECTION_LOST", message: "MQTT 连接中断",
		}
	}
	for sessionID := range g.stoppingSessions {
		g.pendingStatuses[sessionID] = pendingSessionStatus{
			status: "stopped", errorCode: "", message: "推流已停止",
		}
	}
	g.activeSessions = make(map[string]struct{})
	g.startingSessions = make(map[string]int)
	g.stoppingSessions = make(map[string]int)
	g.mu.Unlock()
	if g.dispatcher != nil {
		g.dispatcher.cancelAll()
	}
	if err := g.publisher.StopAll(); err != nil {
		log.Printf("MQTT 断开后停止全部固定摄像头推流失败：%v", err)
	}
}

func (g *Gateway) flushPendingStatuses() {
	g.mu.Lock()
	pending := make(map[string]pendingSessionStatus, len(g.pendingStatuses))
	for sessionID, status := range g.pendingStatuses {
		pending[sessionID] = status
	}
	g.mu.Unlock()
	for sessionID, status := range pending {
		if err := g.publishSessionStatus(sessionID, status.status, "", "", status.errorCode, status.message); err != nil {
			log.Printf("补报固定摄像头会话状态失败，会话ID=%s：%v", sessionID, err)
			continue
		}
		g.mu.Lock()
		if g.pendingStatuses[sessionID] == status {
			delete(g.pendingStatuses, sessionID)
		}
		g.mu.Unlock()
	}
}

func (g *Gateway) handleStart(ctx context.Context, priority commandPriority) paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var command model.StartCommand
		if err := json.Unmarshal(msg.Payload(), &command); err != nil {
			log.Printf("解析固定摄像头启动命令失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		if strings.TrimSpace(command.SessionID) == "" {
			log.Printf("拒绝缺少会话ID的固定摄像头启动命令")
			return
		}
		g.beginStarting(command.SessionID)
		err := g.submitCommand(command.SessionID, command.CommandID, priority, commandJob{
			ctx:       ctx,
			sessionID: command.SessionID,
			run: func(jobCtx context.Context) {
				g.start(jobCtx, command)
			},
		})
		if err != nil {
			g.endStarting(command.SessionID)
			if err != errDuplicateCommand {
				g.status(command.SessionID, "failed", "", "", "GATEWAY_COMMAND_QUEUE_FULL", err.Error())
			}
		}
	}
}

func (g *Gateway) start(ctx context.Context, command model.StartCommand) {
	defer g.endStarting(command.SessionID)
	cameraID := firstNonBlank(command.SourceID, command.DeviceID)
	log.Printf("开始固定摄像头推流，摄像头ID=%s 会话ID=%s 清晰度=%s", cameraID, command.SessionID, command.Quality)
	rtspURL := strings.TrimSpace(command.RTSPURL)
	if rtspURL == "" {
		var err error
		rtspURL, err = g.rtspURL(ctx, cameraID, command.Quality)
		if err != nil {
			if ctx.Err() == nil {
				g.status(command.SessionID, "failed", "", "", "FIXED_CAMERA_CONFIG_FAILED", err.Error())
			}
			return
		}
	}
	// Publisher 启动本身已会读取 RTSP，启动前再执行 ffprobe 只会重复建连。
	// 摄像头健康由后台探测统一负责，会话启动只回报实际 Publisher 结果。
	g.status(command.SessionID, "publishing", "", "", "", "开始启动推流进程")
	_, trackName, err := g.publisher.Start(ctx, command, rtspURL)
	if err != nil {
		if ctx.Err() == nil {
			g.status(command.SessionID, "failed", "", "", "PUBLISH_FAILED", err.Error())
		}
		return
	}
	if ctx.Err() != nil {
		_ = g.publisher.Stop(command.SessionID)
		return
	}
	if !g.rememberSessionIfConnected(ctx, command.SessionID) {
		_ = g.publisher.Stop(command.SessionID)
		return
	}
	// gstreamer-publisher 无法返回 LiveKit 的真实 Track SID，不能把本地占位符当作轨道存在依据。
	// Media Service 会通过 Room API 校验真实轨道，避免网关重启后复用已失效的会话。
	g.status(command.SessionID, "streaming", "", trackName, "", "推流进程已启动，等待视频轨道确认")
}

func (g *Gateway) handleStop(ctx context.Context) paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		var payload model.StopCommand
		if err := json.Unmarshal(msg.Payload(), &payload); err != nil {
			log.Printf("解析固定摄像头停止命令失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		if strings.TrimSpace(payload.SessionID) == "" {
			log.Printf("拒绝缺少会话ID的固定摄像头停止命令")
			return
		}
		g.beginStopping(payload.SessionID)
		err := g.submitCommand(payload.SessionID, payload.CommandID, priorityCommand, commandJob{
			ctx:       ctx,
			sessionID: payload.SessionID,
			run: func(context.Context) {
				defer g.endStopping(payload.SessionID)
				g.stop(payload)
			},
		})
		if err != nil {
			if err == errDuplicateCommand {
				g.endStopping(payload.SessionID)
				return
			}
			log.Printf("固定摄像头停止命令队列已满，立即执行精确停止，会话ID=%s", payload.SessionID)
			g.stop(payload)
			g.endStopping(payload.SessionID)
		}
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
	sessionIDs := g.trackedSessionIDs()
	if err := g.publisher.StopAll(); err != nil {
		log.Printf("停止全部固定摄像头推流进程失败：%v", err)
	}
	for _, sessionID := range sessionIDs {
		g.status(sessionID, "stopped", "", "", "GATEWAY_STOPPED", message)
		g.forgetSession(sessionID)
	}
}

func (g *Gateway) trackedSessionIDs() []string {
	g.mu.Lock()
	defer g.mu.Unlock()
	ids := make(map[string]struct{}, len(g.activeSessions)+len(g.startingSessions)+len(g.stoppingSessions))
	for sessionID := range g.activeSessions {
		ids[sessionID] = struct{}{}
	}
	for sessionID := range g.startingSessions {
		ids[sessionID] = struct{}{}
	}
	for sessionID := range g.stoppingSessions {
		ids[sessionID] = struct{}{}
	}
	result := make([]string, 0, len(ids))
	for sessionID := range ids {
		result = append(result, sessionID)
	}
	return result
}

func (g *Gateway) rememberSession(sessionID string) {
	if strings.TrimSpace(sessionID) == "" {
		return
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	g.activeSessions[sessionID] = struct{}{}
}

func (g *Gateway) rememberSessionIfConnected(ctx context.Context, sessionID string) bool {
	if strings.TrimSpace(sessionID) == "" {
		return false
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	if ctx.Err() != nil {
		return false
	}
	g.activeSessions[sessionID] = struct{}{}
	return true
}

func (g *Gateway) beginStarting(sessionID string) {
	g.mu.Lock()
	g.startingSessions[sessionID]++
	g.mu.Unlock()
}

func (g *Gateway) endStarting(sessionID string) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.startingSessions[sessionID] <= 1 {
		delete(g.startingSessions, sessionID)
		return
	}
	g.startingSessions[sessionID]--
}

func (g *Gateway) beginStopping(sessionID string) {
	g.mu.Lock()
	g.stoppingSessions[sessionID]++
	g.mu.Unlock()
}

func (g *Gateway) endStopping(sessionID string) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.stoppingSessions[sessionID] <= 1 {
		delete(g.stoppingSessions, sessionID)
		return
	}
	g.stoppingSessions[sessionID]--
}

func (g *Gateway) submitCommand(
	sessionID, commandID string,
	priority commandPriority,
	job commandJob,
) error {
	g.mu.Lock()
	defer g.mu.Unlock()
	if commandID != "" && commandID == g.lastCmds[sessionID] {
		return errDuplicateCommand
	}
	if g.dispatcher == nil {
		return fmt.Errorf("固定摄像头命令调度器尚未启动")
	}
	if err := g.dispatcher.submit(priority, job); err != nil {
		return err
	}
	if commandID != "" {
		g.lastCmds[sessionID] = commandID
	}
	return nil
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
	groups := make(map[string][]cameraRecord)
	for _, camera := range g.cameras() {
		rtspURL, reasonCode := rtspURLForCamera(camera, "sub")
		if reasonCode != "" {
			g.publishCameraHealth(g.cameraHealthStatus(camera, "UNKNOWN", reasonCode))
			continue
		}
		groups[rtspURL] = append(groups[rtspURL], camera)
	}
	workers := g.cfg.HealthProbeWorkers
	if workers < 1 {
		workers = 1
	}
	semaphore := make(chan struct{}, workers)
	var wait sync.WaitGroup
	for rtspURL, cameras := range groups {
		rtspURL := rtspURL
		cameras := cameras
		wait.Add(1)
		go func() {
			defer wait.Done()
			select {
			case semaphore <- struct{}{}:
				defer func() { <-semaphore }()
			case <-ctx.Done():
				return
			}
			health := "AVAILABLE"
			reasonCode := ""
			if _, err := g.probe.Check(ctx, rtspURL); err != nil {
				health = "UNAVAILABLE"
				reasonCode = "RTSP_PROBE_FAILED"
			}
			for _, camera := range cameras {
				g.publishCameraHealth(g.cameraHealthStatus(camera, health, reasonCode))
			}
		}()
	}
	wait.Wait()
}

func (g *Gateway) cameraHealthStatus(camera cameraRecord, health, reasonCode string) model.FixedCameraHealthStatus {
	return model.FixedCameraHealthStatus{
		Version: "1.0", GatewayID: g.cfg.GatewayID, CameraID: firstNonBlank(camera.CameraID, camera.ID),
		Health: health, Sequence: g.sequence.Add(1), CheckedAt: time.Now(), ReasonCode: reasonCode,
	}
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
	if err := g.publishSessionStatus(sessionID, status, trackSid, trackName, errorCode, message); err != nil {
		log.Printf("发布固定摄像头视频状态失败，会话ID=%s 状态=%s：%v", sessionID, status, err)
	}
}

func (g *Gateway) publishSessionStatus(sessionID, status, trackSid, trackName, errorCode, message string) error {
	return g.publish("gateway/fixed-camera/"+g.cfg.GatewayID+"/video/status", model.StatusMessage{
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

func firstNonBlank(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return value
		}
	}
	return ""
}

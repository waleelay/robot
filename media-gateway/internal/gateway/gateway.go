package gateway

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
	"media-gateway/internal/config"
	"media-gateway/internal/media"
	"media-gateway/internal/model"
	"media-gateway/internal/publisher"
	"media-gateway/internal/source"
)

type Gateway struct {
	cfg              config.Config
	publisher        publisher.Publisher
	mqtt             paho.Client
	mu               sync.Mutex
	commandStates    map[string]sessionCommandState
	activeSessions   map[string]struct{}
	startingSessions map[string]int
	stoppingSessions map[string]int
	pendingStatuses  map[string]pendingSessionStatus
	// statusFlushMu 串行化补报；不得持有 mu 等待 MQTT，避免回调与状态发送形成锁环。
	statusFlushMu      sync.Mutex
	dispatcher         *commandDispatcher
	connectionCancel   context.CancelFunc
	subscriptionsReady atomic.Bool
	sequence           atomic.Uint64
	probeRunning       atomic.Bool
	// sources 在初始化时必填，保存源配置、协议适配器和当前授权租约。
	sources            *source.Registry
	sessionSources     map[string]model.StopCommand
	leaseBoundSessions map[string]bool
}

type pendingSessionStatus struct {
	status    string
	errorCode string
	message   string
}

type sessionCommandState struct {
	lastCommandID string
	stopped       bool
}

func newGateway(cfg config.Config, pub publisher.Publisher) *Gateway {
	if strings.TrimSpace(cfg.GatewayID) == "" {
		cfg.GatewayID = "default"
	}
	return &Gateway{
		cfg:                cfg,
		publisher:          pub,
		commandStates:      make(map[string]sessionCommandState),
		activeSessions:     make(map[string]struct{}),
		startingSessions:   make(map[string]int),
		stoppingSessions:   make(map[string]int),
		pendingStatuses:    make(map[string]pendingSessionStatus),
		sessionSources:     make(map[string]model.StopCommand),
		leaseBoundSessions: make(map[string]bool),
	}
}

// NewMediaGateway 创建媒体与无人机共享的运行核心；源配置和协议身份在启动时校验。
func NewMediaGateway(cfg config.Config, registry *source.Registry, pub publisher.Publisher) (*Gateway, error) {
	if !source.ValidID(cfg.GatewayID) || !source.ValidID(cfg.ClientID) || registry == nil || pub == nil {
		return nil, fmt.Errorf("媒体网关标识或源注册表无效")
	}
	g := newGateway(cfg, pub)
	g.sources = registry
	registry.RequireAuthorizedCatalog()
	return g, nil
}

func (g *Gateway) topicPrefix() string { return "gateway/media/" + g.cfg.GatewayID }

// Ready 只在 MQTT 已连接且全部业务主题订阅完成时返回 true。
func (g *Gateway) Ready() bool { return g.subscriptionsReady.Load() }

func (g *Gateway) Run(ctx context.Context) error {
	g.subscriptionsReady.Store(false)
	g.dispatcher = newCommandDispatcher(ctx)
	defer g.dispatcher.close()
	gatewayID := strings.TrimSpace(g.cfg.GatewayID)
	if gatewayID == "" {
		gatewayID = "default"
	}
	startTopic := g.topicPrefix() + "/video/start"
	stopTopic := g.topicPrefix() + "/video/stop"
	restartTopic := g.topicPrefix() + "/video/restart"
	catalogTopic := g.topicPrefix() + "/catalog/sync"
	opts := paho.NewClientOptions().
		AddBroker(g.cfg.MQTTBroker).
		SetClientID(g.cfg.ClientID).
		SetAutoReconnect(true).
		SetConnectRetry(false).
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
		log.Printf("媒体网关 MQTT 连接已断开：%v", err)
		g.handleConnectionLost()
	})
	offlinePayload, _ := json.Marshal(model.GatewayStatus{
		Version: "1.0", GatewayID: gatewayID, Status: "OFFLINE", ReasonCode: "MQTT_CONNECTION_LOST",
	})
	opts.SetWill(g.topicPrefix()+"/status", string(offlinePayload), 1, false)
	opts.SetOnConnectHandler(func(_ paho.Client) {
		connectionCtx := g.beginConnection(ctx)
		go g.restoreSubscriptions(connectionCtx, startTopic, stopTopic, restartTopic, catalogTopic)
	})
	mqttClient := paho.NewClient(opts)
	g.mu.Lock()
	g.mqtt = mqttClient
	g.mu.Unlock()
	defer mqttClient.Disconnect(250)
	token := mqttClient.Connect()
	select {
	case <-ctx.Done():
		return nil
	case <-token.Done():
		if token.Error() != nil {
			return token.Error()
		}
	}
	log.Printf("媒体网关已连接 MQTT，网关ID=%s", gatewayID)
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
	go g.probeAllSources(ctx)
	for {
		select {
		case <-ctx.Done():
			g.subscriptionsReady.Store(false)
			g.dispatcher.close()
			g.stopAllPublishers("网关进程停止")
			g.publishGatewayStatus("OFFLINE", "PROCESS_STOPPED")
			return nil
		case <-heartbeatTicker.C:
			g.sweepRevokedSources()
			if g.subscriptionsReady.Load() {
				g.flushPendingStatuses()
				g.publishGatewayStatus("ONLINE", "")
			}
		case <-probeTicker.C:
			go g.probeAllSources(ctx)
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
		if ctx.Err() != nil {
			return
		}
		err := g.subscribe(startTopic, g.handleStart(ctx, normalCommand))
		if err == nil {
			err = g.subscribe(stopTopic, g.handleStop(ctx))
		}
		if err == nil {
			err = g.subscribe(restartTopic, g.handleRestart(ctx))
		}
		if err == nil && catalogTopic != "" {
			err = g.subscribe(catalogTopic, g.handleMediaCatalog(ctx))
		}
		if err == nil {
			if ctx.Err() != nil {
				return
			}
			g.subscriptionsReady.Store(true)
			log.Printf("媒体网关 MQTT 主题订阅已恢复")
			g.flushPendingStatuses()
			g.publishGatewayStatus("ONLINE", "")
			return
		}
		log.Printf("恢复媒体 MQTT 订阅失败，5 秒后重试：%v", err)
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
		g.keepDisconnectedStatusLocked(sessionID, pendingSessionStatus{
			status: "interrupted", errorCode: "MQTT_CONNECTION_LOST", message: "MQTT 连接中断",
		})
	}
	for sessionID := range g.startingSessions {
		g.keepDisconnectedStatusLocked(sessionID, pendingSessionStatus{
			status: "interrupted", errorCode: "MQTT_CONNECTION_LOST", message: "MQTT 连接中断",
		})
	}
	for sessionID := range g.stoppingSessions {
		g.keepDisconnectedStatusLocked(sessionID, pendingSessionStatus{
			status: "stopped", errorCode: "", message: "推流已停止",
		})
	}
	// 断线后 Publisher 已全部停止，允许 Control 用原 commandId 补发未终止会话。
	// 显式 stop 的会话继续保留终止标记，迟到的 start/restart 仍会被拒绝。
	for sessionID, state := range g.commandStates {
		if !state.stopped {
			state.lastCommandID = ""
			g.commandStates[sessionID] = state
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
		log.Printf("MQTT 断开后停止全部媒体推流失败：%v", err)
	}
}

// keepDisconnectedStatusLocked 的调用方须持有 mu；断线报告不能覆盖已排队的撤权终止事实。
func (g *Gateway) keepDisconnectedStatusLocked(sessionID string, status pendingSessionStatus) {
	if previous, exists := g.pendingStatuses[sessionID]; exists && previous.status == "stopped" {
		return
	}
	g.pendingStatuses[sessionID] = status
}

func (g *Gateway) flushPendingStatuses() {
	g.statusFlushMu.Lock()
	defer g.statusFlushMu.Unlock()
	g.mu.Lock()
	ids := make([]string, 0, len(g.pendingStatuses))
	for sessionID := range g.pendingStatuses {
		ids = append(ids, sessionID)
	}
	g.mu.Unlock()
	for _, sessionID := range ids {
		// 发送前读取最新事实，避免较早的中断快照覆盖后来排队的租约终止状态。
		g.mu.Lock()
		status, exists := g.pendingStatuses[sessionID]
		g.mu.Unlock()
		if !exists {
			continue
		}
		if err := g.publishSessionStatus(sessionID, status.status, "", "", status.errorCode, status.message); err != nil {
			log.Printf("补报媒体会话状态失败，会话ID=%s：%v", sessionID, err)
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
	return g.handlePublishCommand(ctx, priority, false)
}

func (g *Gateway) handleRestart(ctx context.Context) paho.MessageHandler {
	return g.handlePublishCommand(ctx, priorityCommand, true)
}

func (g *Gateway) handlePublishCommand(ctx context.Context, priority commandPriority, forceRestart bool) paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		if msg.Retained() {
			log.Printf("拒绝 retained 媒体启动命令")
			return
		}
		var command model.StartCommand
		if err := json.Unmarshal(msg.Payload(), &command); err != nil {
			log.Printf("解析媒体启动命令失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		if strings.TrimSpace(command.SessionID) == "" {
			log.Printf("拒绝缺少会话ID的媒体启动命令")
			return
		}
		if err := g.validateMediaStart(command); err != nil {
			// 未校验的身份不得污染既有会话状态；MQTT 回调也不能同步等待发布确认。
			log.Printf("拒绝无效媒体启动命令，原因码=%s", media.Reason(err))
			return
		}
		g.beginStarting(command.SessionID)
		err := g.submitCommand(command.SessionID, command.CommandID, priority, commandJob{
			ctx:       ctx,
			sessionID: command.SessionID,
			run: func(jobCtx context.Context) {
				g.startPublisher(jobCtx, command, forceRestart)
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
	g.startPublisher(ctx, command, false)
}

func (g *Gateway) startPublisher(ctx context.Context, command model.StartCommand, forceRestart bool) {
	defer g.endStarting(command.SessionID)
	if g.sessionStopped(command.SessionID) {
		log.Printf("忽略已终止会话的迟到启动命令，会话ID=%s", command.SessionID)
		return
	}
	cameraID := firstNonBlank(command.SourceID, command.DeviceID)
	log.Printf("开始媒体推流，摄像头ID=%s 会话ID=%s 清晰度=%s", cameraID, command.SessionID, command.Quality)
	input, err := g.resolveMediaInput(ctx, command)
	if err == nil {
		g.mu.Lock()
		g.leaseBoundSessions[command.SessionID] = true
		g.mu.Unlock()
		input, err = g.sources.Probe(ctx, input)
	}
	if err != nil {
		if ctx.Err() == nil {
			g.status(command.SessionID, "failed", "", "", media.Reason(err), "媒体输入探测失败")
		}
		return
	}
	if !g.canPublishMediaInput(ctx, command, input) {
		return
	}
	message := "开始启动推流进程"
	if forceRestart {
		message = "开始重新启动推流进程"
	}
	g.status(command.SessionID, "publishing", "", "", "", message)
	// publishing 的 MQTT 确认可能等待数秒；紧邻发布调用再次检查，不能依赖探测前的租约。
	if !g.canPublishMediaInput(ctx, command, input) {
		return
	}
	var trackName string
	if forceRestart {
		_, trackName, err = g.publisher.Restart(ctx, command, input)
	} else {
		_, trackName, err = g.publisher.Start(ctx, command, input)
	}
	if err != nil {
		if ctx.Err() == nil {
			g.status(command.SessionID, "failed", "", "", media.ReasonOr(err, "PUBLISH_FAILED"), err.Error())
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
		if msg.Retained() {
			log.Printf("拒绝 retained 媒体停止命令")
			return
		}
		var payload model.StopCommand
		if err := json.Unmarshal(msg.Payload(), &payload); err != nil {
			log.Printf("解析媒体停止命令失败，主题=%s 载荷字节数=%d：%v", msg.Topic(), len(msg.Payload()), err)
			return
		}
		if strings.TrimSpace(payload.SessionID) == "" {
			log.Printf("拒绝缺少会话ID的媒体停止命令")
			return
		}
		if err := g.bindMediaSession(payload); err != nil {
			log.Printf("拒绝媒体源身份不一致的停止命令")
			return
		}
		g.markSessionStopped(payload.SessionID)
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
			log.Printf("媒体停止命令队列已满，立即执行精确停止，会话ID=%s", payload.SessionID)
			g.stop(payload)
			g.endStopping(payload.SessionID)
		}
	}
}

func (g *Gateway) stop(payload model.StopCommand) {
	log.Printf("停止媒体推流，摄像头ID=%s 会话ID=%s", payload.SourceID, payload.SessionID)
	if err := g.publisher.Stop(payload.SessionID); err != nil {
		log.Printf("停止媒体推流进程失败，摄像头ID=%s 会话ID=%s：%v", payload.SourceID, payload.SessionID, err)
	}
	g.forgetSession(payload.SessionID)
	g.status(payload.SessionID, "stopped", "", "", "", "推流已停止")
}

// stopAllPublishers 在 MQTT 仍连接时先收口会话，避免留下没有实际推流的 STREAMING 状态。
func (g *Gateway) stopAllPublishers(message string) {
	sessionIDs := g.trackedSessionIDs()
	if err := g.publisher.StopAll(); err != nil {
		log.Printf("停止全部媒体推流进程失败：%v", err)
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
	if g.commandStates[sessionID].stopped {
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
	state := g.commandStates[sessionID]
	if commandID != "" && commandID == state.lastCommandID {
		return errDuplicateCommand
	}
	if g.dispatcher == nil {
		return fmt.Errorf("媒体命令调度器尚未启动")
	}
	if err := g.dispatcher.submit(priority, job); err != nil {
		return err
	}
	if commandID != "" {
		state.lastCommandID = commandID
		g.commandStates[sessionID] = state
	}
	return nil
}

func (g *Gateway) markSessionStopped(sessionID string) {
	g.mu.Lock()
	defer g.mu.Unlock()
	state := g.commandStates[sessionID]
	state.stopped = true
	g.commandStates[sessionID] = state
}

func (g *Gateway) sessionStopped(sessionID string) bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.commandStates[sessionID].stopped
}

func (g *Gateway) forgetSession(sessionID string) {
	g.mu.Lock()
	defer g.mu.Unlock()
	delete(g.activeSessions, sessionID)
	delete(g.leaseBoundSessions, sessionID)
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

func (g *Gateway) probeAllSources(ctx context.Context) {
	if !g.probeRunning.CompareAndSwap(false, true) {
		return
	}
	defer g.probeRunning.Store(false)
	g.probeMediaSources(ctx)
}

func (g *Gateway) publishGatewayStatus(status, reasonCode string) {
	if err := g.publish(g.topicPrefix()+"/status", model.GatewayStatus{
		Version: "1.0", GatewayID: g.cfg.GatewayID, Status: status,
		Sequence: g.sequence.Add(1), ReportedAt: time.Now(), ReasonCode: reasonCode,
	}); err != nil {
		log.Printf("发布媒体网关状态失败，网关ID=%s 状态=%s：%v", g.cfg.GatewayID, status, err)
	}
}

func (g *Gateway) status(sessionID, status, trackSid, trackName, errorCode, message string) {
	if err := g.publishSessionStatus(sessionID, status, trackSid, trackName, errorCode, message); err != nil {
		log.Printf("发布媒体视频状态失败，会话ID=%s 状态=%s：%v", sessionID, status, err)
	}
}

func (g *Gateway) publishSessionStatus(sessionID, status, trackSid, trackName, errorCode, message string) error {
	payload := model.StatusMessage{
		SessionID: sessionID,
		Status:    status,
		TrackSid:  trackSid,
		TrackName: trackName,
		ErrorCode: errorCode,
		Message:   message,
		Timestamp: time.Now(),
	}
	g.mu.Lock()
	identity := g.sessionSources[sessionID]
	g.mu.Unlock()
	payload.SourceType, payload.SourceID, payload.DeviceID = identity.SourceType, identity.SourceID, identity.DeviceID
	return g.publish(g.topicPrefix()+"/video/status", payload)
}

func (g *Gateway) publish(topic string, payload any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		log.Printf("序列化媒体 MQTT 状态失败，主题=%s：%v", topic, err)
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

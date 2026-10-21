package gateway

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
	"media-gateway/internal/media"
	"media-gateway/internal/model"
	"media-gateway/internal/source"
)

func (g *Gateway) handleMediaCatalog(ctx context.Context) paho.MessageHandler {
	return func(_ paho.Client, msg paho.Message) {
		if msg.Retained() || len(msg.Payload()) > 1<<20 {
			log.Printf("拒绝 retained 或过大的媒体源目录")
			return
		}
		var snapshot source.CatalogSnapshot
		if err := json.Unmarshal(msg.Payload(), &snapshot); err != nil {
			log.Printf("媒体源目录 JSON 无效")
			return
		}
		if err := g.sources.ApplyCatalog(snapshot, g.cfg.GatewayID, time.Now()); err != nil {
			log.Printf("媒体源目录被拒绝：%v", err)
			return
		}
		go func() { g.sweepRevokedSources(); g.probeAllSources(ctx) }()
	}
}

// sweepRevokedSources 先设终止水位并取消启动，再精确停止失去源租约的绑定，防止迟到命令复活。
func (g *Gateway) sweepRevokedSources() {
	g.mu.Lock()
	bindings := make(map[string]model.StopCommand, len(g.sessionSources))
	for id, identity := range g.sessionSources {
		if !g.commandStates[id].stopped && g.leaseBoundSessions[id] {
			bindings[id] = identity
		}
	}
	g.mu.Unlock()
	revoked := false
	for id, identity := range bindings {
		if _, err := g.sources.Lookup(identity.SourceType, identity.SourceID, identity.DeviceID); err != nil {
			g.revokeMediaSession(id, media.Reason(err))
			revoked = true
		}
	}
	if revoked {
		g.flushPendingStatuses()
	}
}

// revokeMediaSession 先原子保存终止水位与待补报事实，再取消启动和释放绑定；发布失败不丢撤权原因。
func (g *Gateway) revokeMediaSession(sessionID, reason string) {
	g.mu.Lock()
	state := g.commandStates[sessionID]
	state.stopped = true
	g.commandStates[sessionID] = state
	g.pendingStatuses[sessionID] = pendingSessionStatus{
		status: "stopped", errorCode: reason, message: "媒体源授权租约已撤销或过期",
	}
	g.mu.Unlock()
	if g.dispatcher != nil {
		g.dispatcher.cancelSession(sessionID)
	}
	if err := g.publisher.Stop(sessionID); err != nil {
		log.Printf("租约失效后停止媒体推流失败，会话ID=%s：%v", sessionID, err)
	}
	g.forgetSession(sessionID)
}

// canPublishMediaInput 校验探测后的有效授权及实际选流，旧输入不能在租约过期或目录变更后继续发布。
func (g *Gateway) canPublishMediaInput(ctx context.Context, command model.StartCommand, input media.Input) bool {
	if ctx.Err() != nil || g.sessionStopped(command.SessionID) {
		return false
	}
	current, err := g.sources.Resolve(command.SourceType, command.SourceID, command.DeviceID, command.SourceProtocol, command.Quality)
	if err == nil && (current.Protocol != input.Protocol || current.Address != input.Address) {
		err = &media.Failure{Code: "SOURCE_INPUT_CHANGED", Text: "媒体源输入已变化，需要重新探测后启动"}
	}
	if err == nil {
		return true
	}
	if media.Reason(err) == "SOURCE_LEASE_MISSING" {
		g.revokeMediaSession(command.SessionID, media.Reason(err))
		g.flushPendingStatuses()
	} else {
		g.status(command.SessionID, "failed", "", "", media.Reason(err), "媒体输入在发布前已失效")
	}
	return false
}

func (g *Gateway) validateMediaStart(command model.StartCommand) error {
	if !source.ValidID(command.SessionID) || !source.ValidID(command.CommandID) ||
		command.RTSPURL != "" || command.Channel != "visible" || command.RoomName == "" ||
		command.PublisherToken == "" || command.LiveKitURL == "" || !command.ExpiresAt.After(time.Now().Add(30*time.Second)) {
		return &media.Failure{Code: "MEDIA_COMMAND_INVALID", Text: "媒体启动命令不完整或包含未授权地址"}
	}
	if _, err := g.sources.Resolve(command.SourceType, command.SourceID, command.DeviceID,
		command.SourceProtocol, command.Quality); err != nil {
		// 目录与 start 属于不同 Topic，格式有效的固定源在工作线程内短暂等待租约到达。
		if media.Reason(err) != "SOURCE_LEASE_MISSING" || !g.sources.Supports(command.SourceProtocol) ||
			(command.Quality != "main" && command.Quality != "sub" && command.Quality != "auto") {
			return err
		}
	}
	return g.bindMediaSession(model.StopCommand{
		SessionID: command.SessionID, SourceType: command.SourceType,
		SourceID: command.SourceID, DeviceID: command.DeviceID,
	})
}

func (g *Gateway) resolveMediaInput(ctx context.Context, command model.StartCommand) (media.Input, error) {
	timeout := g.cfg.ProbeTimeout
	if timeout <= 0 {
		timeout = 8 * time.Second
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	for {
		input, err := g.sources.Resolve(command.SourceType, command.SourceID, command.DeviceID, command.SourceProtocol, command.Quality)
		if err == nil || media.Reason(err) != "SOURCE_LEASE_MISSING" {
			return input, err
		}
		select {
		case <-ctx.Done():
			return media.Input{}, err
		case <-time.After(25 * time.Millisecond):
		}
	}
}

// bindMediaSession 保证同一会话不能跨源复用；stop 先登记身份，迟到 start 仍受终止水位保护。
func (g *Gateway) bindMediaSession(command model.StopCommand) error {
	if !source.ValidID(command.SessionID) || !source.ValidID(command.SourceID) || !source.ValidID(command.DeviceID) ||
		(command.SourceType != "FIXED_CAMERA" && command.SourceType != "DRONE_CAMERA") {
		return &media.Failure{Code: "MEDIA_COMMAND_INVALID", Text: "媒体命令源标识无效"}
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	previous, exists := g.sessionSources[command.SessionID]
	if exists && (previous.SourceType != command.SourceType || previous.SourceID != command.SourceID ||
		previous.DeviceID != command.DeviceID) {
		return &media.Failure{Code: "MEDIA_SESSION_SOURCE_MISMATCH", Text: "会话不能跨媒体源复用"}
	}
	g.sessionSources[command.SessionID] = command
	return nil
}

// probeMediaSources 按输入地址合并探测并限制并发；类型相同、ID 相同但摄像头不同的结果仍分别上报。
func (g *Gateway) probeMediaSources(ctx context.Context) {
	type probeGroup struct {
		input   media.Input
		sources []source.Config
	}
	groups := make(map[string]*probeGroup)
	for _, cfg := range g.sources.Sources() {
		input, err := g.sources.Resolve(cfg.SourceType, cfg.SourceID, cfg.DeviceID, cfg.Protocol, "sub")
		if err != nil {
			g.publishSourceHealth(cfg, "UNKNOWN", media.Reason(err))
			continue
		}
		key := input.Protocol + "|" + input.Address
		if groups[key] == nil {
			groups[key] = &probeGroup{input: input}
		}
		groups[key].sources = append(groups[key].sources, cfg)
	}
	workers := g.cfg.HealthProbeWorkers
	if workers < 1 {
		workers = 1
	}
	semaphore := make(chan struct{}, workers)
	var wait sync.WaitGroup
	for _, group := range groups {
		wait.Add(1)
		go func(group *probeGroup) {
			defer wait.Done()
			select {
			case semaphore <- struct{}{}:
				defer func() { <-semaphore }()
			case <-ctx.Done():
				return
			}
			health, reason := "AVAILABLE", ""
			if _, err := g.sources.Probe(ctx, group.input); err != nil {
				health, reason = "UNAVAILABLE", media.Reason(err)
			}
			if ctx.Err() != nil {
				return
			}
			for _, cfg := range group.sources {
				g.publishSourceHealth(cfg, health, reason)
			}
		}(group)
	}
	wait.Wait()
}

func (g *Gateway) publishSourceHealth(cfg source.Config, health, reason string) {
	status := model.SourceHealthStatus{
		Version: "1.0", GatewayID: g.cfg.GatewayID, SourceType: cfg.SourceType, SourceID: cfg.SourceID,
		DeviceID: cfg.DeviceID, SourceProtocol: cfg.Protocol, Health: health,
		Sequence: g.sequence.Add(1), CheckedAt: time.Now(), ReasonCode: reason,
	}
	topic := fmt.Sprintf("%s/source/%s/%s/%s/status", g.topicPrefix(), cfg.SourceType, cfg.SourceID, cfg.DeviceID)
	// 探测结果只包含脱敏字段，发布失败不输出媒体输入地址。
	if err := g.publish(topic, status); err != nil {
		log.Printf("媒体源健康发布失败，源类型=%s 源ID=%s 摄像头ID=%s：%v", cfg.SourceType, cfg.SourceID, cfg.DeviceID, err)
	}
}

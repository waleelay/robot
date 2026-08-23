package publisher

import (
	"context"
	"errors"
	"log"
	"os/exec"
	"strings"
	"sync"
	"syscall"
	"time"

	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
)

type Publisher interface {
	Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error)
	Stop(sessionID string) error
	StopStream(command model.StopCommand) error
	StopAll() error
}

// ProcessPublisher 用外部进程完成 RTSP -> LiveKit 的发布。
// 默认走 gst-launch/livekit-gstreamer-publisher，也可以通过 PUBLISHER_CMD 注入自定义命令。
type ProcessPublisher struct {
	cfg                    *config.Config
	cmds                   map[string]*processEntry
	sessions               map[string]string
	streamSessions         map[string]map[string]struct{}
	gstreamerFailedRTSPURL map[string]time.Time
	mu                     sync.Mutex
}

type processEntry struct {
	cmd       *exec.Cmd
	done      chan error
	mode      string
	expiresAt time.Time
}

func NewProcessPublisher(cfg config.Config) *ProcessPublisher {
	return &ProcessPublisher{
		cfg:                    &cfg,
		cmds:                   make(map[string]*processEntry),
		sessions:               make(map[string]string),
		streamSessions:         make(map[string]map[string]struct{}),
		gstreamerFailedRTSPURL: make(map[string]time.Time),
	}
}

func (p *ProcessPublisher) Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	key := streamKey(command)
	previousKey := p.unbindSessionLocked(command.SessionID)
	if previousKey != "" && previousKey != key && len(p.streamSessions[previousKey]) == 0 {
		_ = p.stopStreamLocked(previousKey)
	}
	if entry := p.cmds[key]; entry != nil {
		if entryRunning(entry) && tokenUsable(entry.expiresAt) && tokenUsable(command.ExpiresAt) {
			p.bindSessionLocked(command.SessionID, key)
			return "TR_" + command.SessionID, trackName(command), nil
		}
		_ = p.stopStreamLocked(key)
	}
	trackName := "video." + command.Channel + "." + command.Quality
	if p.cfg.PublisherCmd != "" {
		return p.startCommand(ctx, command, rtspURL, trackName, key, p.cfg.PublisherCmd, "custom")
	}
	if p.cfg.PublisherMode == "ffmpeg" {
		if p.cfg.FFmpegPublisherCmd == "" {
			return "", "", errors.New("PUBLISHER_MODE=ffmpeg requires FFMPEG_PUBLISHER_CMD")
		}
		return p.startCommand(ctx, command, rtspURL, trackName, key, p.cfg.FFmpegPublisherCmd, "ffmpeg")
	}
	if p.cfg.PublisherMode != "auto" && p.cfg.PublisherMode != "gstreamer" {
		return "", "", errors.New("unsupported PUBLISHER_MODE: " + p.cfg.PublisherMode)
	}
	if p.shouldStartWithFFmpeg(command, rtspURL) {
		return p.startCommand(ctx, command, rtspURL, trackName, key, p.cfg.FFmpegPublisherCmd, "ffmpeg-first")
	}
	trackSid, publishedTrackName, err := p.startGStreamer(ctx, command, rtspURL, trackName, key)
	if err == nil {
		if p.cfg.PublisherMode == "auto" && p.cfg.FFmpegPublisherCmd != "" {
			p.watchGStreamerForFallback(ctx, command, rtspURL, trackName, key)
		}
		return trackSid, publishedTrackName, nil
	}
	if p.cfg.PublisherMode == "gstreamer" || p.cfg.FFmpegPublisherCmd == "" {
		return trackSid, publishedTrackName, err
	}
	p.gstreamerFailedRTSPURL[rtspURL] = time.Now()
	log.Printf("GStreamer 推流失败，回退到 FFmpeg，会话ID=%s：%v", command.SessionID, err)
	return p.startCommand(ctx, command, rtspURL, trackName, key, p.cfg.FFmpegPublisherCmd, "ffmpeg")
}

func (p *ProcessPublisher) startCommand(ctx context.Context, command model.StartCommand, rtspURL string, trackName string, key string, template string, mode string) (string, string, error) {
	args := strings.Fields(template)
	if len(args) == 0 {
		return "", "", errors.New("publisher command is empty")
	}
	for i := range args {
		args[i] = strings.NewReplacer(
			"{rtsp}", rtspURL,
			"{livekitUrl}", command.LiveKitURL,
			"{token}", command.PublisherToken,
			"{room}", command.RoomName,
			"{track}", trackName,
		).Replace(args[i])
	}
	cmd := exec.CommandContext(ctx, args[0], args[1:]...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := cmd.Start(); err != nil {
		return "", "", err
	}
	// 外部进程参数和输出可能包含 RTSP 凭据或 LiveKit Token，不写入应用日志。
	log.Printf("推流进程已启动，模式=%s 会话ID=%s", mode, command.SessionID)
	entry := newProcessEntry(cmd, mode, command.ExpiresAt)
	p.cmds[key] = entry
	p.bindSessionLocked(command.SessionID, key)
	if err := p.ensureRunning(key, entry); err != nil {
		return "", "", err
	}
	return "TR_" + command.SessionID, trackName, nil
}

func (p *ProcessPublisher) startGStreamer(ctx context.Context, command model.StartCommand, rtspURL string, trackName string, key string) (string, string, error) {
	// 默认 pipeline 只描述 GStreamer 的媒体处理部分；LiveKit URL/token 由 publisher 工具参数提供。
	pipeline := strings.NewReplacer(
		"{rtsp}", rtspURL,
		"{livekitUrl}", command.LiveKitURL,
		"{token}", command.PublisherToken,
		"{room}", command.RoomName,
		"{track}", trackName,
	).Replace(p.cfg.GStreamerPipeline)
	args := []string{"--url", command.LiveKitURL, "--token", command.PublisherToken, "--"}
	args = append(args, strings.Fields(pipeline)...)
	cmd := exec.CommandContext(ctx, p.cfg.GStreamerPublisherPath, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := cmd.Start(); err != nil {
		return "", "", err
	}
	// 外部进程参数和输出可能包含 RTSP 凭据或 LiveKit Token，不写入应用日志。
	log.Printf("GStreamer 推流进程已启动，会话ID=%s", command.SessionID)
	entry := newProcessEntry(cmd, "gstreamer", command.ExpiresAt)
	p.cmds[key] = entry
	p.bindSessionLocked(command.SessionID, key)
	if err := p.ensureRunning(key, entry); err != nil {
		return "", "", err
	}
	return "TR_" + command.SessionID, trackName, nil
}

func (p *ProcessPublisher) shouldStartWithFFmpeg(command model.StartCommand, rtspURL string) bool {
	if p.cfg.PublisherMode != "auto" || p.cfg.FFmpegPublisherCmd == "" {
		return false
	}
	if failedAt, failed := p.gstreamerFailedRTSPURL[rtspURL]; failed {
		if time.Since(failedAt) < p.cfg.PublisherGStreamerRetry {
			return true
		}
		delete(p.gstreamerFailedRTSPURL, rtspURL)
		log.Printf("FFmpeg 回退冷却结束，重新尝试 GStreamer，会话ID=%s", command.SessionID)
	}
	return p.cfg.PublisherFFmpegFirstIDs[command.DeviceID]
}

func newProcessEntry(cmd *exec.Cmd, mode string, expiresAt time.Time) *processEntry {
	entry := &processEntry{cmd: cmd, done: make(chan error, 1), mode: mode, expiresAt: expiresAt}
	go func() {
		entry.done <- cmd.Wait()
	}()
	return entry
}

func (p *ProcessPublisher) watchGStreamerForFallback(ctx context.Context, command model.StartCommand, rtspURL string, trackName string, key string) {
	entry := p.cmds[key]
	if entry == nil {
		return
	}
	go p.fallbackIfGStreamerExits(ctx, command, rtspURL, trackName, key, entry)
}

func (p *ProcessPublisher) fallbackIfGStreamerExits(ctx context.Context, command model.StartCommand, rtspURL string, trackName string, key string, entry *processEntry) {
	select {
	case err := <-entry.done:
		p.mu.Lock()
		defer p.mu.Unlock()
		if p.cmds[key] != entry {
			return
		}
		delete(p.cmds, key)
		p.gstreamerFailedRTSPURL[rtspURL] = time.Now()
		log.Printf("GStreamer 推流进程退出，自动回退到 FFmpeg，会话ID=%s：%v", command.SessionID, err)
		if _, _, startErr := p.startCommand(ctx, command, rtspURL, trackName, key, p.cfg.FFmpegPublisherCmd, "ffmpeg"); startErr != nil {
			log.Printf("自动回退到 FFmpeg 失败，会话ID=%s：%v", command.SessionID, startErr)
		}
	case <-time.After(p.cfg.PublisherFallbackWatch):
	case <-ctx.Done():
	}
}

func (p *ProcessPublisher) Stop(sessionID string) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	key := p.sessions[sessionID]
	p.unbindSessionLocked(sessionID)
	if key == "" || len(p.streamSessions[key]) > 0 {
		return nil
	}
	return p.stopStreamLocked(key)
}

func (p *ProcessPublisher) StopStream(command model.StopCommand) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	key := stopStreamKey(command)
	for sessionID := range p.streamSessions[key] {
		delete(p.sessions, sessionID)
	}
	delete(p.streamSessions, key)
	return p.stopStreamLocked(key)
}

func (p *ProcessPublisher) StopAll() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	// map 在遍历时会被 stopLocked 删除。Go 允许删除当前 map key，这里用于快速清空。
	for key := range p.cmds {
		_ = p.stopStreamLocked(key)
	}
	p.sessions = make(map[string]string)
	p.streamSessions = make(map[string]map[string]struct{})
	return nil
}

func (p *ProcessPublisher) stopStreamLocked(key string) error {
	entry := p.cmds[key]
	var err error
	if entry != nil && entry.cmd != nil && entry.cmd.Process != nil {
		err = syscall.Kill(-entry.cmd.Process.Pid, syscall.SIGKILL)
		if err != nil {
			err = entry.cmd.Process.Kill()
		}
	}
	delete(p.cmds, key)
	for sessionID, sessionKey := range p.sessions {
		if sessionKey == key {
			delete(p.sessions, sessionID)
		}
	}
	delete(p.streamSessions, key)
	return err
}

func (p *ProcessPublisher) ensureRunning(key string, entry *processEntry) error {
	select {
	case err := <-entry.done:
		// 进程两秒内退出通常表示 pipeline 参数、RTSP 或 token 有问题，直接回报失败。
		if p.cmds[key] == entry {
			p.stopStreamLocked(key)
		}
		if err == nil {
			return errors.New("publisher exited")
		}
		return err
	case <-time.After(2 * time.Second):
		// 运行超过两秒认为启动成功，后续异常会通过进程退出日志和服务端超时机制兜底。
		return nil
	}
}

func streamKey(command model.StartCommand) string {
	sourceType := command.SourceType
	if sourceType == "" {
		sourceType = "ROBOT"
	}
	sourceID := command.SourceID
	if sourceID == "" {
		sourceID = command.DeviceID
	}
	if sourceID == "" {
		sourceID = command.RobotID
	}
	roomName := command.RoomName
	if roomName == "" {
		roomName = command.Channel + "|" + command.Quality
	}
	return strings.Join([]string{sourceType, sourceID, roomName}, "|")
}

func stopStreamKey(command model.StopCommand) string {
	return streamKey(model.StartCommand{
		SourceType: command.SourceType,
		SourceID:   command.SourceID,
		DeviceID:   command.DeviceID,
		RoomName:   command.RoomName,
	})
}

func trackName(command model.StartCommand) string {
	return "video." + command.Channel + "." + command.Quality
}

func tokenUsable(expiresAt time.Time) bool {
	return expiresAt.IsZero() || expiresAt.After(time.Now().Add(30*time.Second))
}

func entryRunning(entry *processEntry) bool {
	return entry != nil && entry.cmd != nil && entry.cmd.Process != nil && entry.cmd.ProcessState == nil
}

func (p *ProcessPublisher) bindSessionLocked(sessionID string, key string) {
	if sessionID == "" {
		return
	}
	p.sessions[sessionID] = key
	if p.streamSessions[key] == nil {
		p.streamSessions[key] = make(map[string]struct{})
	}
	p.streamSessions[key][sessionID] = struct{}{}
}

func (p *ProcessPublisher) unbindSessionLocked(sessionID string) string {
	key := p.sessions[sessionID]
	if key == "" {
		return ""
	}
	delete(p.sessions, sessionID)
	delete(p.streamSessions[key], sessionID)
	if len(p.streamSessions[key]) == 0 {
		delete(p.streamSessions, key)
	}
	return key
}

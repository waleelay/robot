package publisher

import (
	"context"
	"errors"
	"hash/fnv"
	"log"
	"os"
	"os/exec"
	"regexp"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"fixed-camera-gateway/internal/config"
	"fixed-camera-gateway/internal/model"
)

type Publisher interface {
	Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error)
	Stop(sessionID string) error
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
	unexpectedExits        uint64
	tokenExpirations       uint64
	forcedKills            uint64
	lastCleanupMillis      int64
	maxCleanupMillis       int64
	events                 chan LifecycleEvent
	startLocks             [8]sync.Mutex
	mu                     sync.Mutex
}

type processEntry struct {
	cmd          *exec.Cmd
	done         chan processResult
	mode         string
	expiresAt    time.Time
	stderr       *tailBuffer
	redactValues []string
	running      atomic.Bool
}

type processResult struct {
	err         error
	exitedAt    time.Time
	diagnostics string
}

const maxProcessStderrBytes = 4096

// 子进程启动只做快速失败保护；是否真正发布出视频轨道由 Media Service 通过
// LiveKit Room API 确认，避免这里长时间占用 Gateway 命令工作线程。
const publisherStartupGuard = 500 * time.Millisecond

var processURLPattern = regexp.MustCompile(`(?i)(?:rtsp|rtsps|https?|wss?)://[^\s]+`)

// tailBuffer 保留子进程最近的输出，避免异常时大量 stderr 占用网关内存。
type tailBuffer struct {
	data []byte
}

func (b *tailBuffer) Write(data []byte) (int, error) {
	b.data = append(b.data, data...)
	if overflow := len(b.data) - maxProcessStderrBytes; overflow > 0 {
		b.data = append([]byte(nil), b.data[overflow:]...)
	}
	return len(data), nil
}

func (b *tailBuffer) String() string {
	return string(b.data)
}

type Snapshot struct {
	ActivePublishers  int    `json:"activePublishers"`
	ActiveSessions    int    `json:"activeSessions"`
	UnexpectedExits   uint64 `json:"unexpectedExits"`
	TokenExpirations  uint64 `json:"tokenExpirations"`
	ForcedKills       uint64 `json:"forcedKills"`
	LastCleanupMillis int64  `json:"lastCleanupMillis"`
	MaxCleanupMillis  int64  `json:"maxCleanupMillis"`
}

type LifecycleEvent struct {
	SessionIDs []string
	ReasonCode string
	Message    string
}

func NewProcessPublisher(cfg config.Config) *ProcessPublisher {
	return &ProcessPublisher{
		cfg:                    &cfg,
		cmds:                   make(map[string]*processEntry),
		sessions:               make(map[string]string),
		streamSessions:         make(map[string]map[string]struct{}),
		gstreamerFailedRTSPURL: make(map[string]time.Time),
		events:                 make(chan LifecycleEvent, 64),
	}
}

func (p *ProcessPublisher) Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error) {
	if !tokenUsable(command.ExpiresAt) {
		return "", "", errors.New("发布 Token 缺失、已过期或剩余有效期不足 30 秒")
	}
	key := streamKey(command)
	startLock := &p.startLocks[streamLockIndex(key)]
	startLock.Lock()
	defer startLock.Unlock()

	p.mu.Lock()
	previousKey := p.unbindSessionLocked(command.SessionID)
	if previousKey != "" && previousKey != key && len(p.streamSessions[previousKey]) == 0 {
		_ = p.stopStreamLocked(previousKey)
	}
	if entry := p.cmds[key]; entry != nil {
		if entryRunning(entry) && tokenUsable(entry.expiresAt) && tokenUsable(command.ExpiresAt) {
			p.bindSessionLocked(command.SessionID, key)
			p.mu.Unlock()
			return "TR_" + command.SessionID, trackName(command), nil
		}
		_ = p.stopStreamLocked(key)
	}
	p.mu.Unlock()

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
			p.watchGStreamerForFallback(command, rtspURL, trackName, key)
		} else if entry := p.cmds[key]; entry != nil {
			p.watchProcessExit(key, entry, command.SessionID)
		}
		return trackSid, publishedTrackName, nil
	}
	if p.cfg.PublisherMode == "gstreamer" || p.cfg.FFmpegPublisherCmd == "" {
		return trackSid, publishedTrackName, err
	}
	p.mu.Lock()
	p.gstreamerFailedRTSPURL[rtspURL] = time.Now()
	p.mu.Unlock()
	log.Printf("GStreamer 推流失败，回退到 FFmpeg，会话ID=%s：%v", command.SessionID, err)
	return p.startCommand(ctx, command, rtspURL, trackName, key, p.cfg.FFmpegPublisherCmd, "ffmpeg")
}

func (p *ProcessPublisher) startCommand(ctx context.Context, command model.StartCommand, rtspURL string, trackName string, key string, template string, mode string) (string, string, error) {
	if err := ctx.Err(); err != nil {
		return "", "", err
	}
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
	// 启动命令上下文只负责中断启动阶段；推流进程由 Stop、StopAll 和 Token
	// 到期任务显式管理，不能在命令工作线程返回时跟随临时上下文被杀死。
	cmd := exec.Command(args[0], args[1:]...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	cmd.Env = directMediaProcessEnv()
	entry := newProcessEntry(cmd, mode, command.ExpiresAt, command, rtspURL)
	if err := cmd.Start(); err != nil {
		return "", "", err
	}
	entry.startWaiting()
	// 外部进程参数和输出可能包含 RTSP 凭据或 LiveKit Token，不写入应用日志。
	log.Printf("推流进程已启动，模式=%s 会话ID=%s", mode, command.SessionID)
	p.mu.Lock()
	p.cmds[key] = entry
	p.bindSessionLocked(command.SessionID, key)
	p.mu.Unlock()
	if err := p.ensureRunning(key, entry); err != nil {
		return "", "", err
	}
	p.watchProcessExit(key, entry, command.SessionID)
	p.watchTokenExpiry(key, entry, command.SessionID)
	return "TR_" + command.SessionID, trackName, nil
}

func (p *ProcessPublisher) startGStreamer(ctx context.Context, command model.StartCommand, rtspURL string, trackName string, key string) (string, string, error) {
	if err := ctx.Err(); err != nil {
		return "", "", err
	}
	// 默认 pipeline 只描述 GStreamer 的媒体处理部分；LiveKit URL/token 由 publisher 工具参数提供。
	pipeline := strings.NewReplacer(
		"{rtsp}", rtspURL,
		"{livekitUrl}", command.LiveKitURL,
		"{token}", command.PublisherToken,
		"{room}", command.RoomName,
		"{track}", trackName,
	).Replace(p.cfg.GStreamerPipeline)
	args := []string{"--url", command.LiveKitURL, "--token", command.PublisherToken}
	args = append(args, "--")
	args = append(args, strings.Fields(pipeline)...)
	cmd := exec.Command(p.cfg.GStreamerPublisherPath, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	cmd.Env = directMediaProcessEnv()
	entry := newProcessEntry(cmd, "gstreamer", command.ExpiresAt, command, rtspURL)
	if err := cmd.Start(); err != nil {
		return "", "", err
	}
	entry.startWaiting()
	// 外部进程参数和输出可能包含 RTSP 凭据或 LiveKit Token，不写入应用日志。
	log.Printf("GStreamer 推流进程已启动，会话ID=%s", command.SessionID)
	p.mu.Lock()
	p.cmds[key] = entry
	p.bindSessionLocked(command.SessionID, key)
	p.mu.Unlock()
	if err := p.ensureRunning(key, entry); err != nil {
		return "", "", err
	}
	p.watchTokenExpiry(key, entry, command.SessionID)
	return "TR_" + command.SessionID, trackName, nil
}

func (p *ProcessPublisher) shouldStartWithFFmpeg(command model.StartCommand, rtspURL string) bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.cfg.PublisherMode != "auto" || p.cfg.FFmpegPublisherCmd == "" {
		return false
	}
	if failedAt, failed := p.gstreamerFailedRTSPURL[rtspURL]; failed {
		if time.Since(failedAt) < p.cfg.PublisherRetryInterval {
			return true
		}
		delete(p.gstreamerFailedRTSPURL, rtspURL)
		log.Printf("FFmpeg 回退冷却结束，重新尝试 GStreamer，会话ID=%s", command.SessionID)
	}
	return false
}

func newProcessEntry(cmd *exec.Cmd, mode string, expiresAt time.Time, command model.StartCommand, rtspURL string) *processEntry {
	stderr := &tailBuffer{}
	// 部分外部 CLI 会把致命错误写到 stdout；统一收集以免诊断丢失。
	cmd.Stdout = stderr
	cmd.Stderr = stderr
	entry := &processEntry{
		cmd: cmd, done: make(chan processResult, 1), mode: mode, expiresAt: expiresAt, stderr: stderr,
		redactValues: []string{command.PublisherToken, rtspURL, command.LiveKitURL},
	}
	return entry
}

func (p *processEntry) startWaiting() {
	p.running.Store(true)
	go func() {
		result := processResult{err: p.cmd.Wait(), exitedAt: time.Now(), diagnostics: p.diagnostics()}
		p.running.Store(false)
		p.done <- result
	}()
}

func (p *processEntry) diagnostics() string {
	output := strings.TrimSpace(p.stderr.String())
	for _, value := range p.redactValues {
		if value != "" {
			output = strings.ReplaceAll(output, value, "***")
		}
	}
	output = processURLPattern.ReplaceAllString(output, "<url>")
	return strings.TrimSpace(output)
}

func processExitError(result processResult) string {
	if result.err == nil {
		return "进程已退出"
	}
	if result.diagnostics == "" {
		return result.err.Error()
	}
	return result.err.Error() + "；stderr=" + result.diagnostics
}

// LiveKit 信令和 WebRTC 媒体必须直连；HTTP 代理无法承载 UDP 候选协商。
// 构建镜像时可保留代理，但推流子进程不能继承这些代理环境变量。
func directMediaProcessEnv() []string {
	return withoutProxyEnv(os.Environ())
}

func withoutProxyEnv(environ []string) []string {
	result := make([]string, 0, len(environ))
	for _, item := range environ {
		name, _, found := strings.Cut(item, "=")
		if found {
			switch strings.ToLower(name) {
			case "http_proxy", "https_proxy", "all_proxy", "no_proxy":
				continue
			}
		}
		result = append(result, item)
	}
	return result
}

func (p *ProcessPublisher) watchGStreamerForFallback(command model.StartCommand, rtspURL string, trackName string, key string) {
	p.mu.Lock()
	entry := p.cmds[key]
	p.mu.Unlock()
	if entry == nil {
		return
	}
	go p.fallbackIfGStreamerExits(command, rtspURL, trackName, key, entry)
}

func (p *ProcessPublisher) fallbackIfGStreamerExits(command model.StartCommand, rtspURL string, trackName string, key string, entry *processEntry) {
	select {
	case result := <-entry.done:
		startLock := &p.startLocks[streamLockIndex(key)]
		startLock.Lock()
		defer startLock.Unlock()
		p.mu.Lock()
		if p.cmds[key] != entry {
			p.mu.Unlock()
			return
		}
		sessionIDs := p.sessionIDsLocked(key)
		delete(p.cmds, key)
		p.gstreamerFailedRTSPURL[rtspURL] = time.Now()
		p.recordCleanupLocked(result.exitedAt)
		p.unexpectedExits++
		p.mu.Unlock()
		log.Printf("GStreamer 推流进程异常退出，自动回退到 FFmpeg，会话ID=%s：%s", command.SessionID, processExitError(result))
		if _, _, startErr := p.startCommand(context.Background(), command, rtspURL, trackName, key, p.cfg.FFmpegPublisherCmd, "ffmpeg"); startErr != nil {
			log.Printf("自动回退到 FFmpeg 失败，会话ID=%s：%v", command.SessionID, startErr)
			p.mu.Lock()
			p.unbindStreamLocked(key)
			p.emitLocked(LifecycleEvent{SessionIDs: sessionIDs, ReasonCode: "PUBLISH_PROCESS_EXITED", Message: "推流进程异常退出且回退失败"})
			p.mu.Unlock()
		}
	case <-time.After(p.cfg.PublisherFallbackWatch):
		p.watchProcessExit(key, entry, command.SessionID)
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
		err = syscall.Kill(-entry.cmd.Process.Pid, syscall.SIGTERM)
		if err != nil {
			err = entry.cmd.Process.Signal(syscall.SIGTERM)
		}
		p.forceKillAfterTimeout(entry)
	}
	delete(p.cmds, key)
	p.unbindStreamLocked(key)
	return err
}

func (p *ProcessPublisher) ensureRunning(key string, entry *processEntry) error {
	timer := time.NewTimer(publisherStartupGuard)
	defer timer.Stop()
	select {
	case result := <-entry.done:
		p.mu.Lock()
		if p.cmds[key] == entry {
			delete(p.cmds, key)
			p.unbindStreamLocked(key)
		}
		p.mu.Unlock()
		if result.err == nil {
			return errors.New("推流进程在启动确认前退出")
		}
		return errors.New(processExitError(result))
	case <-timer.C:
		p.mu.Lock()
		running := p.cmds[key] == entry && entryRunning(entry)
		p.mu.Unlock()
		if !running {
			return errors.New("推流进程在启动确认期间已停止")
		}
		return nil
	}
}

func streamLockIndex(key string) int {
	hash := fnv.New32a()
	_, _ = hash.Write([]byte(key))
	return int(hash.Sum32() % 8)
}

func streamKey(command model.StartCommand) string {
	sourceType := command.SourceType
	if sourceType == "" {
		sourceType = "FIXED_CAMERA"
	}
	sourceID := command.SourceID
	if sourceID == "" {
		sourceID = command.DeviceID
	}
	roomName := command.RoomName
	if roomName == "" {
		roomName = command.Channel + "|" + command.Quality
	}
	return strings.Join([]string{sourceType, sourceID, roomName}, "|")
}

func trackName(command model.StartCommand) string {
	return "video." + command.Channel + "." + command.Quality
}

func tokenUsable(expiresAt time.Time) bool {
	return !expiresAt.IsZero() && expiresAt.After(time.Now().Add(30*time.Second))
}

func entryRunning(entry *processEntry) bool {
	return entry != nil && entry.running.Load()
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

func (p *ProcessPublisher) watchProcessExit(key string, entry *processEntry, sessionID string) {
	go func() {
		result := <-entry.done
		p.mu.Lock()
		defer p.mu.Unlock()
		if p.cmds[key] != entry {
			return
		}
		delete(p.cmds, key)
		sessionIDs := p.sessionIDsLocked(key)
		p.unbindStreamLocked(key)
		p.unexpectedExits++
		p.recordCleanupLocked(result.exitedAt)
		log.Printf("推流进程异常退出并已清理资源，模式=%s 会话ID=%s：%s", entry.mode, sessionID, processExitError(result))
		p.emitLocked(LifecycleEvent{SessionIDs: sessionIDs, ReasonCode: "PUBLISH_PROCESS_EXITED", Message: "推流进程异常退出"})
	}()
}

func (p *ProcessPublisher) watchTokenExpiry(key string, entry *processEntry, sessionID string) {
	delay := time.Until(entry.expiresAt)
	if delay <= 0 {
		delay = time.Millisecond
	}
	go func() {
		timer := time.NewTimer(delay)
		defer timer.Stop()
		<-timer.C
		p.mu.Lock()
		defer p.mu.Unlock()
		if p.cmds[key] != entry {
			return
		}
		p.tokenExpirations++
		log.Printf("发布 Token 到期，开始清理推流资源，会话ID=%s", sessionID)
		p.emitLocked(LifecycleEvent{SessionIDs: p.sessionIDsLocked(key), ReasonCode: "PUBLISH_TOKEN_EXPIRED", Message: "发布 Token 已到期"})
		_ = p.stopStreamLocked(key)
	}()
}

func (p *ProcessPublisher) forceKillAfterTimeout(entry *processEntry) {
	timeout := p.cfg.PublisherStopTimeout
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	go func() {
		timer := time.NewTimer(timeout)
		defer timer.Stop()
		<-timer.C
		if entry.cmd == nil || entry.cmd.Process == nil || entry.cmd.ProcessState != nil {
			return
		}
		if err := syscall.Kill(-entry.cmd.Process.Pid, syscall.SIGKILL); err != nil {
			_ = entry.cmd.Process.Kill()
		}
		p.mu.Lock()
		p.forcedKills++
		p.mu.Unlock()
		log.Printf("推流进程未在宽限期内退出，已强制清理，模式=%s", entry.mode)
	}()
}

func (p *ProcessPublisher) unbindStreamLocked(key string) {
	for sessionID, sessionKey := range p.sessions {
		if sessionKey == key {
			delete(p.sessions, sessionID)
		}
	}
	delete(p.streamSessions, key)
}

func (p *ProcessPublisher) recordCleanupLocked(exitedAt time.Time) {
	millis := time.Since(exitedAt).Milliseconds()
	if millis < 0 {
		millis = 0
	}
	p.lastCleanupMillis = millis
	if millis > p.maxCleanupMillis {
		p.maxCleanupMillis = millis
	}
}

func (p *ProcessPublisher) Snapshot() Snapshot {
	p.mu.Lock()
	defer p.mu.Unlock()
	return Snapshot{
		ActivePublishers: len(p.cmds), ActiveSessions: len(p.sessions),
		UnexpectedExits: p.unexpectedExits, TokenExpirations: p.tokenExpirations,
		ForcedKills: p.forcedKills, LastCleanupMillis: p.lastCleanupMillis,
		MaxCleanupMillis: p.maxCleanupMillis,
	}
}

func (p *ProcessPublisher) Events() <-chan LifecycleEvent {
	return p.events
}

func (p *ProcessPublisher) sessionIDsLocked(key string) []string {
	result := make([]string, 0, len(p.streamSessions[key]))
	for sessionID := range p.streamSessions[key] {
		result = append(result, sessionID)
	}
	return result
}

func (p *ProcessPublisher) emitLocked(event LifecycleEvent) {
	if len(event.SessionIDs) == 0 {
		return
	}
	select {
	case p.events <- event:
	default:
		log.Printf("推流生命周期事件队列已满，事件未下发，原因码=%s", event.ReasonCode)
	}
}

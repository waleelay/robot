package publisher

import (
	"context"
	"errors"
	"log"
	"os"
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
	StopAll() error
}

// ProcessPublisher 用外部进程完成 RTSP -> LiveKit 的发布。
// 默认走 gst-launch/livekit-gstreamer-publisher，也可以通过 PUBLISHER_CMD 注入自定义命令。
type ProcessPublisher struct {
	cfg                    *config.Config
	cmds                   map[string]*processEntry
	gstreamerFailedRTSPURL map[string]bool
	mu                     sync.Mutex
}

type processEntry struct {
	cmd  *exec.Cmd
	done chan error
	mode string
}

func NewProcessPublisher(cfg config.Config) *ProcessPublisher {
	return &ProcessPublisher{
		cfg:                    &cfg,
		cmds:                   make(map[string]*processEntry),
		gstreamerFailedRTSPURL: make(map[string]bool),
	}
}

func (p *ProcessPublisher) Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	_ = p.stopLocked(command.SessionID)
	trackName := "video." + command.Channel + "." + command.Quality
	if p.cfg.PublisherCmd != "" {
		return p.startCommand(ctx, command, rtspURL, trackName, p.cfg.PublisherCmd, "custom")
	}
	if p.cfg.PublisherMode == "ffmpeg" {
		if p.cfg.FFmpegPublisherCmd == "" {
			return "", "", errors.New("PUBLISHER_MODE=ffmpeg requires FFMPEG_PUBLISHER_CMD")
		}
		return p.startCommand(ctx, command, rtspURL, trackName, p.cfg.FFmpegPublisherCmd, "ffmpeg")
	}
	if p.cfg.PublisherMode != "auto" && p.cfg.PublisherMode != "gstreamer" {
		return "", "", errors.New("unsupported PUBLISHER_MODE: " + p.cfg.PublisherMode)
	}
	if p.shouldStartWithFFmpeg(command, rtspURL) {
		return p.startCommand(ctx, command, rtspURL, trackName, p.cfg.FFmpegPublisherCmd, "ffmpeg-first")
	}
	trackSid, publishedTrackName, err := p.startGStreamer(ctx, command, rtspURL, trackName)
	if err == nil {
		if p.cfg.PublisherMode == "auto" && p.cfg.FFmpegPublisherCmd != "" {
			p.watchGStreamerForFallback(ctx, command, rtspURL, trackName)
		}
		return trackSid, publishedTrackName, nil
	}
	if p.cfg.PublisherMode == "gstreamer" || p.cfg.FFmpegPublisherCmd == "" {
		return trackSid, publishedTrackName, err
	}
	p.gstreamerFailedRTSPURL[rtspURL] = true
	log.Println("publisher fallback ffmpeg", command.SessionID, err)
	return p.startCommand(ctx, command, rtspURL, trackName, p.cfg.FFmpegPublisherCmd, "ffmpeg")
}

func (p *ProcessPublisher) startCommand(ctx context.Context, command model.StartCommand, rtspURL string, trackName string, template string, mode string) (string, string, error) {
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
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	log.Println("publisher command", mode, strings.Join(args, " "))
	if err := cmd.Start(); err != nil {
		return "", "", err
	}
	entry := newProcessEntry(cmd, mode)
	p.cmds[command.SessionID] = entry
	if err := p.ensureRunning(command.SessionID, entry); err != nil {
		return "", "", err
	}
	return "TR_" + command.SessionID, trackName, nil
}

func (p *ProcessPublisher) startGStreamer(ctx context.Context, command model.StartCommand, rtspURL string, trackName string) (string, string, error) {
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
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	log.Println("publisher command", p.cfg.GStreamerPublisherPath, strings.Join(args, " "))
	if err := cmd.Start(); err != nil {
		return "", "", err
	}
	entry := newProcessEntry(cmd, "gstreamer")
	p.cmds[command.SessionID] = entry
	if err := p.ensureRunning(command.SessionID, entry); err != nil {
		return "", "", err
	}
	return "TR_" + command.SessionID, trackName, nil
}

func (p *ProcessPublisher) shouldStartWithFFmpeg(command model.StartCommand, rtspURL string) bool {
	if p.cfg.PublisherMode != "auto" || p.cfg.FFmpegPublisherCmd == "" {
		return false
	}
	if p.gstreamerFailedRTSPURL[rtspURL] {
		return true
	}
	return p.cfg.PublisherFFmpegFirstIDs[command.DeviceID]
}

func newProcessEntry(cmd *exec.Cmd, mode string) *processEntry {
	entry := &processEntry{cmd: cmd, done: make(chan error, 1), mode: mode}
	go func() {
		entry.done <- cmd.Wait()
	}()
	return entry
}

func (p *ProcessPublisher) watchGStreamerForFallback(ctx context.Context, command model.StartCommand, rtspURL string, trackName string) {
	entry := p.cmds[command.SessionID]
	if entry == nil {
		return
	}
	go p.fallbackIfGStreamerExits(ctx, command, rtspURL, trackName, entry)
}

func (p *ProcessPublisher) fallbackIfGStreamerExits(ctx context.Context, command model.StartCommand, rtspURL string, trackName string, entry *processEntry) {
	select {
	case err := <-entry.done:
		p.mu.Lock()
		defer p.mu.Unlock()
		if p.cmds[command.SessionID] != entry {
			return
		}
		delete(p.cmds, command.SessionID)
		p.gstreamerFailedRTSPURL[rtspURL] = true
		log.Println("publisher auto fallback ffmpeg", command.SessionID, "gstreamer_exit", err)
		if _, _, startErr := p.startCommand(ctx, command, rtspURL, trackName, p.cfg.FFmpegPublisherCmd, "ffmpeg"); startErr != nil {
			log.Println("publisher auto fallback failed", command.SessionID, startErr)
		}
	case <-time.After(p.cfg.PublisherFallbackWatch):
	case <-ctx.Done():
	}
}

func (p *ProcessPublisher) Stop(sessionID string) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.stopLocked(sessionID)
}

func (p *ProcessPublisher) StopAll() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	// map 在遍历时会被 stopLocked 删除。Go 允许删除当前 map key，这里用于快速清空。
	for sessionID := range p.cmds {
		_ = p.stopLocked(sessionID)
	}
	return nil
}

func (p *ProcessPublisher) stopLocked(sessionID string) error {
	entry := p.cmds[sessionID]
	if entry == nil || entry.cmd == nil || entry.cmd.Process == nil {
		return nil
	}
	err := syscall.Kill(-entry.cmd.Process.Pid, syscall.SIGKILL)
	if err != nil {
		err = entry.cmd.Process.Kill()
	}
	delete(p.cmds, sessionID)
	return err
}

func (p *ProcessPublisher) ensureRunning(sessionID string, entry *processEntry) error {
	select {
	case err := <-entry.done:
		// 进程两秒内退出通常表示 pipeline 参数、RTSP 或 token 有问题，直接回报失败。
		delete(p.cmds, sessionID)
		if err == nil {
			return errors.New("publisher exited")
		}
		return err
	case <-time.After(2 * time.Second):
		// 运行超过两秒认为启动成功，后续异常会通过进程退出日志和服务端超时机制兜底。
		return nil
	}
}

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
	cfg  *config.Config
	cmds map[string]*exec.Cmd
	mu   sync.Mutex
}

func NewProcessPublisher(cfg config.Config) *ProcessPublisher {
	return &ProcessPublisher{cfg: &cfg, cmds: make(map[string]*exec.Cmd)}
}

func (p *ProcessPublisher) Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	_ = p.stopLocked(command.SessionID)
	trackName := "video." + command.Channel + "." + command.Quality
	if p.cfg.PublisherCmd != "" {
		return p.startCommand(ctx, command, rtspURL, trackName, p.cfg.PublisherCmd, "custom")
	}
	trackSid, publishedTrackName, err := p.startGStreamer(ctx, command, rtspURL, trackName)
	if err == nil || p.cfg.FFmpegPublisherCmd == "" {
		return trackSid, publishedTrackName, err
	}
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
	p.cmds[command.SessionID] = cmd
	if err := p.ensureRunning(command.SessionID, cmd); err != nil {
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
	p.cmds[command.SessionID] = cmd
	if err := p.ensureRunning(command.SessionID, cmd); err != nil {
		return "", "", err
	}
	return "TR_" + command.SessionID, trackName, nil
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
	cmd := p.cmds[sessionID]
	if cmd == nil || cmd.Process == nil {
		return nil
	}
	err := syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
	if err != nil {
		err = cmd.Process.Kill()
	}
	delete(p.cmds, sessionID)
	return err
}

func (p *ProcessPublisher) ensureRunning(sessionID string, cmd *exec.Cmd) error {
	done := make(chan error, 1)
	go func(cmd *exec.Cmd) {
		done <- cmd.Wait()
	}(cmd)
	select {
	case err := <-done:
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

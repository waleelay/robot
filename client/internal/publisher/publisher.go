package publisher

import (
	"context"
	"errors"
	"log"
	"os"
	"os/exec"
	"strings"
	"sync"
	"time"

	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
)

type Publisher interface {
	Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error)
	Stop() error
}

type ProcessPublisher struct {
	cfg *config.Config
	cmd *exec.Cmd
	mu  sync.Mutex
}

func NewProcessPublisher(cfg config.Config) *ProcessPublisher {
	return &ProcessPublisher{cfg: &cfg}
}

func (p *ProcessPublisher) Start(ctx context.Context, command model.StartCommand, rtspURL string) (string, string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	_ = p.stopLocked()
	trackName := "video." + command.Channel + "." + command.Quality
	if p.cfg.PublisherCmd == "" {
		return p.startGStreamer(ctx, command, rtspURL, trackName)
	}
	args := strings.Fields(p.cfg.PublisherCmd)
	for i := range args {
		args[i] = strings.NewReplacer(
			"{rtsp}", rtspURL,
			"{livekitUrl}", command.LiveKitURL,
			"{token}", command.PublisherToken,
			"{room}", command.RoomName,
			"{track}", trackName,
		).Replace(args[i])
	}
	p.cmd = exec.CommandContext(ctx, args[0], args[1:]...)
	p.cmd.Stdout = os.Stdout
	p.cmd.Stderr = os.Stderr
	log.Println("publisher command", strings.Join(args, " "))
	if err := p.cmd.Start(); err != nil {
		return "", "", err
	}
	if err := p.ensureRunning(); err != nil {
		return "", "", err
	}
	return "TR_" + command.SessionID, trackName, nil
}

func (p *ProcessPublisher) startGStreamer(ctx context.Context, command model.StartCommand, rtspURL string, trackName string) (string, string, error) {
	pipeline := strings.NewReplacer(
		"{rtsp}", rtspURL,
		"{livekitUrl}", command.LiveKitURL,
		"{token}", command.PublisherToken,
		"{room}", command.RoomName,
		"{track}", trackName,
	).Replace(p.cfg.GStreamerPipeline)
	args := []string{"--url", command.LiveKitURL, "--token", command.PublisherToken, "--"}
	args = append(args, strings.Fields(pipeline)...)
	p.cmd = exec.CommandContext(ctx, p.cfg.GStreamerPublisherPath, args...)
	p.cmd.Stdout = os.Stdout
	p.cmd.Stderr = os.Stderr
	log.Println("publisher command", p.cfg.GStreamerPublisherPath, strings.Join(args, " "))
	if err := p.cmd.Start(); err != nil {
		return "", "", err
	}
	if err := p.ensureRunning(); err != nil {
		return "", "", err
	}
	return "TR_" + command.SessionID, trackName, nil
}

func (p *ProcessPublisher) Stop() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.stopLocked()
}

func (p *ProcessPublisher) stopLocked() error {
	if p.cmd == nil || p.cmd.Process == nil {
		return nil
	}
	err := p.cmd.Process.Kill()
	p.cmd = nil
	return err
}

func (p *ProcessPublisher) ensureRunning() error {
	done := make(chan error, 1)
	go func(cmd *exec.Cmd) {
		done <- cmd.Wait()
	}(p.cmd)
	select {
	case err := <-done:
		p.cmd = nil
		if err == nil {
			return errors.New("publisher exited")
		}
		return err
	case <-time.After(2 * time.Second):
		return nil
	}
}

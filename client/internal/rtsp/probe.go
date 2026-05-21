package rtsp

import (
	"context"
	"os/exec"
	"time"
)

type Probe struct {
	path    string
	timeout time.Duration
}

func NewProbe(path string, timeout time.Duration) *Probe {
	return &Probe{path: path, timeout: timeout}
}

func (p *Probe) Check(ctx context.Context, url string) error {
	ctx, cancel := context.WithTimeout(ctx, p.timeout)
	defer cancel()
	return exec.CommandContext(ctx, p.path, "-v", "error", "-rtsp_transport", "tcp", "-select_streams", "v:0", "-show_entries", "stream=codec_name,width,height", "-of", "json", url).Run()
}

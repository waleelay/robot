package rtsp

import (
	"context"
	"encoding/json"
	"fmt"
	"os/exec"
	"time"
)

type StreamInfo struct {
	CodecName string
	Width     int
	Height    int
}

type Probe struct {
	path    string
	timeout time.Duration
}

func NewProbe(path string, timeout time.Duration) *Probe {
	return &Probe{path: path, timeout: timeout}
}

func (p *Probe) Check(ctx context.Context, url string) (StreamInfo, error) {
	ctx, cancel := context.WithTimeout(ctx, p.timeout)
	defer cancel()
	output, err := exec.CommandContext(ctx, p.path, "-v", "error", "-rtsp_transport", "tcp", "-select_streams", "v:0", "-show_entries", "stream=codec_name,width,height", "-of", "json", url).Output()
	if err != nil {
		return StreamInfo{}, err
	}
	if len(output) > 0 {
		fmt.Print(string(output))
	}
	var payload struct {
		Streams []struct {
			CodecName string `json:"codec_name"`
			Width     int    `json:"width"`
			Height    int    `json:"height"`
		} `json:"streams"`
	}
	if err := json.Unmarshal(output, &payload); err != nil {
		return StreamInfo{}, err
	}
	if len(payload.Streams) == 0 {
		return StreamInfo{}, nil
	}
	stream := payload.Streams[0]
	return StreamInfo{CodecName: stream.CodecName, Width: stream.Width, Height: stream.Height}, nil
}

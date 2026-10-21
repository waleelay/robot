package rtsp

import (
	"context"
	"net/url"
	"strings"

	"media-gateway/internal/media"
)

// Prober 沿用已有 ffprobe 能力，便于在无设备测试中替换真实探测。
type Prober interface {
	Check(context.Context, string) (media.StreamInfo, error)
}

// Adapter 是首批唯一可运行的输入协议，不对尚未实现的协议宣称支持。
type Adapter struct{ prober Prober }

var _ media.Adapter = (*Adapter)(nil)

// NewAdapter 使用现有 RTSP 探测器构建适配器。
func NewAdapter(prober Prober) *Adapter { return &Adapter{prober: prober} }

func (*Adapter) Protocol() string { return "RTSP" }

func (*Adapter) ValidateAddress(raw string) error {
	u, err := url.Parse(raw)
	if err != nil || u == nil || (u.Scheme != "rtsp" && u.Scheme != "rtsps") || u.Hostname() == "" ||
		strings.ContainsAny(raw, "\r\n\t ") {
		return &media.Failure{Code: "SOURCE_URL_INVALID", Text: "RTSP 地址格式无效"}
	}
	return nil
}

func (a *Adapter) Probe(ctx context.Context, raw string) (media.StreamInfo, error) {
	if err := a.ValidateAddress(raw); err != nil {
		return media.StreamInfo{}, err
	}
	info, err := a.prober.Check(ctx, raw)
	if err != nil {
		return media.StreamInfo{}, &media.Failure{Code: "SOURCE_PROBE_FAILED", Text: "RTSP 探测失败"}
	}
	info.CodecName = strings.ToLower(strings.TrimSpace(info.CodecName))
	switch info.CodecName {
	case "h264", "hevc", "h265":
		return info, nil
	case "":
		return media.StreamInfo{}, &media.Failure{Code: "SOURCE_VIDEO_EMPTY", Text: "RTSP 未发现视频轨道"}
	default:
		return media.StreamInfo{}, &media.Failure{Code: "SOURCE_CODEC_UNSUPPORTED", Text: "视频编码尚不支持"}
	}
}

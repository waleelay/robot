package rtsp

import (
	"context"
	"errors"
	"strings"
	"testing"

	"media-gateway/internal/media"
)

type probeStub struct {
	codec string
	err   error
}

func (p probeStub) Check(context.Context, string) (media.StreamInfo, error) {
	return media.StreamInfo{CodecName: p.codec}, p.err
}

func TestAdapterRecognizesCodecAndRedactsProbeFailure(t *testing.T) {
	for _, test := range []struct{ codec, code string }{
		{"h264", ""}, {"hevc", ""}, {"H265", ""}, {"", "SOURCE_VIDEO_EMPTY"}, {"av1", "SOURCE_CODEC_UNSUPPORTED"},
	} {
		adapter := NewAdapter(probeStub{codec: test.codec})
		info, err := adapter.Probe(context.Background(), "rtsp://ground/live")
		if test.code == "" {
			if err != nil || info.CodecName != strings.ToLower(test.codec) {
				t.Fatalf("编码识别失败：%+v %v", info, err)
			}
		} else if media.Reason(err) != test.code {
			t.Fatalf("错误分类不一致：%v", err)
		}
	}
	adapter := NewAdapter(probeStub{err: errors.New("rtsp://user:secret@host token-secret")})
	_, err := adapter.Probe(context.Background(), "rtsp://ground/live")
	if err == nil || strings.Contains(err.Error(), "secret") {
		t.Fatalf("敏感探测异常未脱敏：%v", err)
	}
}

func TestAdapterValidatesRTSPAddress(t *testing.T) {
	adapter := NewAdapter(probeStub{})
	for _, address := range []string{"http://ground/live", "rtsp:///live", "rtsp://ground/live\n"} {
		if err := adapter.ValidateAddress(address); media.Reason(err) != "SOURCE_URL_INVALID" {
			t.Fatalf("无效地址应被分类拒绝：%v", err)
		}
	}
	for _, address := range []string{"rtsp://ground/live", "rtsps://ground/live"} {
		if err := adapter.ValidateAddress(address); err != nil {
			t.Fatal(err)
		}
	}
}

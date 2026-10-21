package source

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"media-gateway/internal/media"
	"media-gateway/internal/protocol/rtsp"
)

type probeStub struct {
	codec string
	err   error
}

func (p probeStub) Check(context.Context, string) (media.StreamInfo, error) {
	return media.StreamInfo{CodecName: p.codec}, p.err
}

func TestSourceRegistrySeparatesDeviceTypeAndCamera(t *testing.T) {
	sources := []Config{
		{SourceType: "FIXED_CAMERA", SourceID: "same-id", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://fixed/main"},
		{SourceType: "DRONE_CAMERA", SourceID: "same-id", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://drone/main", SubURL: "rtsp://drone/sub"},
		{SourceType: "DRONE_CAMERA", SourceID: "same-id", DeviceID: "camera02", Protocol: "RTSP", MainURL: "rtsp://drone/other"},
	}
	r, err := NewRegistry(sources, rtsp.NewAdapter(probeStub{codec: "h264"}))
	if err != nil {
		t.Fatal(err)
	}
	for _, test := range []struct{ typ, device, quality, want string }{
		{"FIXED_CAMERA", "camera01", "sub", "rtsp://fixed/main"},
		{"DRONE_CAMERA", "camera01", "main", "rtsp://drone/main"},
		{"DRONE_CAMERA", "camera01", "auto", "rtsp://drone/sub"},
		{"DRONE_CAMERA", "camera02", "main", "rtsp://drone/other"},
	} {
		input, err := r.Resolve(test.typ, "same-id", test.device, "RTSP", test.quality)
		if err != nil || input.Address != test.want {
			t.Fatalf("源隔离或选流失败：%+v %v", input, err)
		}
	}
	if _, err := r.Resolve("DRONE_CAMERA", "same-id", "camera01", "RTMP", "main"); media.Reason(err) != "SOURCE_PROTOCOL_MISMATCH" {
		t.Fatalf("应拒绝协议不匹配：%v", err)
	}
	if _, err := r.Resolve("DRONE_CAMERA", "missing", "camera01", "RTSP", "main"); media.Reason(err) != "SOURCE_NOT_CONFIGURED" {
		t.Fatalf("应拒绝未配置源：%v", err)
	}
}

func TestRegistryRejectsAmbiguousAndUnsupportedConfig(t *testing.T) {
	base := Config{SourceType: "DRONE_CAMERA", SourceID: "drone01", DeviceID: "camera01", Protocol: "RTSP", MainURL: "rtsp://ground/live"}
	for _, test := range []struct {
		name   string
		modify func(*Config)
	}{
		{"未知类型", func(c *Config) { c.SourceType = "ROBOT_CAMERA" }},
		{"Topic通配符", func(c *Config) { c.SourceID = "drone/+" }},
		{"未实现协议", func(c *Config) { c.Protocol = "SRT" }},
		{"协议伪装", func(c *Config) { c.MainURL = "http://ground/live" }},
		{"空码流", func(c *Config) { c.MainURL = "" }},
	} {
		t.Run(test.name, func(t *testing.T) {
			cfg := base
			test.modify(&cfg)
			if _, err := NewRegistry([]Config{cfg}, rtsp.NewAdapter(probeStub{})); err == nil {
				t.Fatal("应拒绝无效源配置")
			}
		})
	}
	if _, err := NewRegistry([]Config{base, base}, rtsp.NewAdapter(probeStub{})); err == nil {
		t.Fatal("应拒绝重复源")
	}
}

func TestLoadConfigsRejectsTyposTrailingAndOversizedData(t *testing.T) {
	for _, body := range []string{
		`{"sources":[],"sourcez":[]}`, `{"sources":[]} {}`, strings.Repeat(" ", 1<<20+1), `{"sources":[]}`,
	} {
		path := filepath.Join(t.TempDir(), "sources.json")
		if err := os.WriteFile(path, []byte(body), 0600); err != nil {
			t.Fatal(err)
		}
		if _, err := LoadConfigs(path); err == nil {
			t.Fatal("应拒绝无效配置")
		}
	}
	path := filepath.Join(t.TempDir(), "sources.json")
	if err := os.WriteFile(path, []byte(`{"sources":[{"sourceType":"DRONE_CAMERA","sourceId":"drone01","deviceId":"camera01","sourceProtocol":"RTSP","mainStreamUrl":"rtsp://ground/live"}]}`), 0600); err != nil {
		t.Fatal(err)
	}
	if configs, err := LoadConfigs(path); err != nil || len(configs) != 1 {
		t.Fatalf("有效配置读取失败：%v", err)
	}
}

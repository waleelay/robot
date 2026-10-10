// Package source 将业务源标识与输入协议适配分离，不承载用户权限或媒体会话业务。
package source

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"regexp"
	"strings"
	"sync"
	"time"

	"media-gateway/internal/media"
)

// Config 保存受控本地媒体配置；URL 含凭据时必须通过只读挂载交付，禁止输出日志。
type Config struct {
	SourceType string `json:"sourceType"`
	SourceID   string `json:"sourceId"`
	DeviceID   string `json:"deviceId"`
	Protocol   string `json:"sourceProtocol"`
	MainURL    string `json:"mainStreamUrl"`
	SubURL     string `json:"subStreamUrl,omitempty"`
}

// Registry 保存启动时的本地映射，并以读写锁保护动态授权租约；键按源类型、源 ID 和摄像头隔离。
type Registry struct {
	mu                 sync.RWMutex
	sources            map[string]Config
	adapters           map[string]media.Adapter
	leased             map[string]LeasedConfig
	leaseRequired      bool
	lastIssuedAt       time.Time
	lastCatalogVersion uint64
}

var identifier = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$`)

// ValidID 保证标识可以安全用于 MQTT Topic 段，不接受通配符、路径或空白。
func ValidID(value string) bool { return identifier.MatchString(value) }

func key(sourceType, sourceID, deviceID string) string {
	return sourceType + "|" + sourceID + "|" + deviceID
}

// NewRegistry 校验全部源和协议；重复源、不支持的协议或不完整配置使服务启动失败。
func NewRegistry(configs []Config, adapters ...media.Adapter) (*Registry, error) {
	r := &Registry{sources: make(map[string]Config), adapters: make(map[string]media.Adapter), leased: make(map[string]LeasedConfig)}
	for _, adapter := range adapters {
		if adapter == nil || adapter.Protocol() == "" {
			return nil, fmt.Errorf("媒体协议适配器为空")
		}
		if _, exists := r.adapters[adapter.Protocol()]; exists {
			return nil, fmt.Errorf("媒体协议适配器重复")
		}
		r.adapters[adapter.Protocol()] = adapter
	}
	for _, cfg := range configs {
		if cfg.SourceType != "FIXED_CAMERA" && cfg.SourceType != "DRONE_CAMERA" {
			return nil, fmt.Errorf("媒体源类型不支持")
		}
		if !ValidID(cfg.SourceID) || !ValidID(cfg.DeviceID) {
			return nil, fmt.Errorf("媒体源或摄像头标识无效")
		}
		adapter, exists := r.adapters[cfg.Protocol]
		if !exists {
			return nil, fmt.Errorf("媒体输入协议尚未实现")
		}
		if cfg.MainURL == "" && cfg.SubURL == "" {
			return nil, fmt.Errorf("媒体源未配置码流")
		}
		for _, url := range []string{cfg.MainURL, cfg.SubURL} {
			if url != "" {
				if err := adapter.ValidateAddress(url); err != nil {
					return nil, err
				}
			}
		}
		k := key(cfg.SourceType, cfg.SourceID, cfg.DeviceID)
		if _, exists := r.sources[k]; exists {
			return nil, fmt.Errorf("媒体源配置重复")
		}
		r.sources[k] = cfg
	}
	return r, nil
}

// LoadConfigs 读取不超过 1 MiB 的 JSON，拒绝未知字段和尾随数据，防止拼错配置静默生效。
func LoadConfigs(path string) ([]Config, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("媒体源配置文件无法打开")
	}
	defer f.Close()
	body, err := io.ReadAll(io.LimitReader(f, (1<<20)+1))
	if err != nil || len(body) > 1<<20 {
		return nil, fmt.Errorf("媒体源配置读取失败或超过 1 MiB")
	}
	var document struct {
		Sources []Config `json:"sources"`
	}
	decoder := json.NewDecoder(strings.NewReader(string(body)))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&document); err != nil {
		return nil, fmt.Errorf("媒体源配置 JSON 无效")
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		return nil, fmt.Errorf("媒体源配置包含尾随数据")
	}
	if len(document.Sources) == 0 {
		return nil, fmt.Errorf("媒体源配置不能为空")
	}
	return document.Sources, nil
}

// Resolve 只读取本地映射，禁止 MQTT 注入任意网络地址；缺少指定码流时选择另一条有效源。
func (r *Registry) Resolve(sourceType, sourceID, deviceID, protocol, quality string) (media.Input, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	cfg, err := r.lookupLocked(sourceType, sourceID, deviceID)
	if err != nil {
		return media.Input{}, err
	}
	if cfg.Protocol != protocol {
		return media.Input{}, &media.Failure{Code: "SOURCE_PROTOCOL_MISMATCH", Text: "命令协议与媒体源配置不一致"}
	}
	if quality != "main" && quality != "sub" && quality != "auto" {
		return media.Input{}, &media.Failure{Code: "SOURCE_QUALITY_UNSUPPORTED", Text: "不支持的媒体清晰度"}
	}
	address := cfg.MainURL
	if quality != "main" && cfg.SubURL != "" || address == "" {
		address = cfg.SubURL
	}
	return media.Input{Protocol: cfg.Protocol, Address: address}, nil
}

// Lookup 验证当前源配置和授权租约，不假定输入协议或清晰度，供生命周期与恢复使用。
func (r *Registry) Lookup(sourceType, sourceID, deviceID string) (Config, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.lookupLocked(sourceType, sourceID, deviceID)
}

// Supports 只判断已注册的适配器能力，不表示发布后端已经支持该协议。
func (r *Registry) Supports(protocol string) bool { _, ok := r.adapters[protocol]; return ok }

func (r *Registry) lookupLocked(sourceType, sourceID, deviceID string) (Config, error) {
	k := key(sourceType, sourceID, deviceID)
	cfg, exists := r.sources[k]
	if lease, ok := r.leased[k]; ok && lease.ExpiresAt.After(time.Now()) {
		cfg, exists = lease.Config, true
	} else if sourceType == "FIXED_CAMERA" && r.leaseRequired {
		return Config{}, &media.Failure{Code: "SOURCE_LEASE_MISSING", Text: "媒体源授权租约缺失或已过期"}
	}
	if !exists {
		return Config{}, &media.Failure{Code: "SOURCE_NOT_CONFIGURED", Text: "媒体源未配置"}
	}
	return cfg, nil
}

// Probe 通过真实协议适配器探测并生成已验证编码的输入。
func (r *Registry) Probe(ctx context.Context, input media.Input) (media.Input, error) {
	adapter, exists := r.adapters[input.Protocol]
	if !exists {
		return media.Input{}, &media.Failure{Code: "SOURCE_PROTOCOL_UNSUPPORTED", Text: "媒体输入协议尚未实现"}
	}
	info, err := adapter.Probe(ctx, input.Address)
	if err != nil {
		return media.Input{}, err
	}
	input.Info = info
	return input, nil
}

// Sources 返回配置副本供健康探测使用，不允许调用方修改映射。
func (r *Registry) Sources() []Config {
	r.mu.RLock()
	defer r.mu.RUnlock()
	result := make([]Config, 0, len(r.sources))
	merged := make(map[string]Config)
	for k, cfg := range r.sources {
		if cfg.SourceType != "FIXED_CAMERA" || !r.leaseRequired {
			merged[k] = cfg
		}
	}
	for k, lease := range r.leased {
		if lease.ExpiresAt.After(time.Now()) {
			merged[k] = lease.Config
		}
	}
	for _, cfg := range merged {
		result = append(result, cfg)
	}
	return result
}

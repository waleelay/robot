package source

import (
	"fmt"
	"media-gateway/internal/media"
	"time"
)

// LeasedConfig 是平台已授权的临时媒体源；失效后不能由本地固定源配置降级继续使用。
type LeasedConfig struct {
	Config
	Enabled   bool      `json:"enabled"`
	ExpiresAt time.Time `json:"expiresAt"`
}

// CatalogSnapshot 是 Control 合并 BFF 有效授权租约后发布的通用全量目录。
type CatalogSnapshot struct {
	Version        string         `json:"version"`
	GatewayID      string         `json:"gatewayId"`
	CatalogVersion uint64         `json:"catalogVersion"`
	IssuedAt       time.Time      `json:"issuedAt"`
	Sources        []LeasedConfig `json:"sources"`
}

// RequireAuthorizedCatalog 在生产统一入口强制固定摄像头使用平台租约，不允许本地配置绕过撤权。
func (r *Registry) RequireAuthorizedCatalog() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.leaseRequired = true
}

// ApplyCatalog 原子替换有效租约集合；拒绝跨 Gateway、陈旧版本、超长租约和无效源，不部分应用。
func (r *Registry) ApplyCatalog(snapshot CatalogSnapshot, gatewayID string, now time.Time) error {
	if snapshot.Version != "1.0" || snapshot.GatewayID != gatewayID || snapshot.CatalogVersion == 0 || snapshot.IssuedAt.IsZero() ||
		snapshot.IssuedAt.After(now.Add(30*time.Second)) || snapshot.IssuedAt.Before(now.Add(-300*time.Second)) || len(snapshot.Sources) > 5000 {
		return fmt.Errorf("媒体目录身份、版本或签发时间无效")
	}
	configs := make([]Config, 0, len(snapshot.Sources))
	next := make(map[string]LeasedConfig)
	for _, entry := range snapshot.Sources {
		if !entry.Enabled || !entry.ExpiresAt.After(now) {
			continue
		}
		if entry.ExpiresAt.After(snapshot.IssuedAt.Add(300 * time.Second)) {
			return fmt.Errorf("媒体源租约超过 300 秒边界")
		}
		configs = append(configs, entry.Config)
		next[key(entry.SourceType, entry.SourceID, entry.DeviceID)] = entry
	}
	adapters := make([]media.Adapter, 0, len(r.adapters))
	for _, adapter := range r.adapters {
		adapters = append(adapters, adapter)
	}
	if _, err := NewRegistry(configs, adapters...); err != nil {
		return err
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if !r.lastIssuedAt.IsZero() && (snapshot.IssuedAt.Before(r.lastIssuedAt) ||
		snapshot.IssuedAt.Equal(r.lastIssuedAt) && snapshot.CatalogVersion <= r.lastCatalogVersion) {
		return fmt.Errorf("媒体目录版本未递增")
	}
	r.leased = next
	r.lastIssuedAt = snapshot.IssuedAt
	r.lastCatalogVersion = snapshot.CatalogVersion
	return nil
}

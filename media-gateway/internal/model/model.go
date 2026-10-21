package model

import "time"

type StartCommand struct {
	CommandID      string `json:"commandId"`
	SessionID      string `json:"sessionId"`
	SourceType     string `json:"sourceType"`
	SourceProtocol string `json:"sourceProtocol,omitempty"`
	SourceID       string `json:"sourceId"`
	DeviceID       string `json:"deviceId"`
	Channel        string `json:"channel"`
	Quality        string `json:"quality"`
	LiveKitURL     string `json:"livekitUrl"`
	RoomName       string `json:"roomName"`
	PublisherToken string `json:"publisherToken"`
	// RTSPURL 仅用于识别并拒绝旧命令中的地址注入，不参与输入解析或发布。
	RTSPURL   string    `json:"rtspUrl,omitempty"`
	ExpiresAt time.Time `json:"expiresAt"`
}

type StopCommand struct {
	CommandID  string `json:"commandId"`
	SessionID  string `json:"sessionId"`
	SourceType string `json:"sourceType,omitempty"`
	SourceID   string `json:"sourceId,omitempty"`
	DeviceID   string `json:"deviceId,omitempty"`
	RoomName   string `json:"roomName"`
}

type StatusMessage struct {
	SourceType string    `json:"sourceType,omitempty"`
	SourceID   string    `json:"sourceId,omitempty"`
	DeviceID   string    `json:"deviceId,omitempty"`
	SessionID  string    `json:"sessionId"`
	Status     string    `json:"status"`
	TrackSid   string    `json:"trackSid,omitempty"`
	TrackName  string    `json:"trackName,omitempty"`
	ErrorCode  string    `json:"errorCode,omitempty"`
	Message    string    `json:"message,omitempty"`
	Timestamp  time.Time `json:"timestamp"`
}

type GatewayStatus struct {
	Version    string    `json:"version"`
	GatewayID  string    `json:"gatewayId"`
	Status     string    `json:"status"`
	Sequence   uint64    `json:"sequence"`
	ReportedAt time.Time `json:"reportedAt"`
	ReasonCode string    `json:"reasonCode,omitempty"`
}

// SourceHealthStatus 按业务源、摄像头和输入协议隔离统一媒体网关的健康结果。
type SourceHealthStatus struct {
	Version        string    `json:"version"`
	GatewayID      string    `json:"gatewayId"`
	SourceType     string    `json:"sourceType"`
	SourceID       string    `json:"sourceId"`
	DeviceID       string    `json:"deviceId"`
	SourceProtocol string    `json:"sourceProtocol"`
	Health         string    `json:"health"`
	Sequence       uint64    `json:"sequence"`
	CheckedAt      time.Time `json:"checkedAt"`
	ReasonCode     string    `json:"reasonCode,omitempty"`
}

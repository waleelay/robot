package model

import "time"

type StartCommand struct {
	CommandID      string    `json:"commandId"`
	SessionID      string    `json:"sessionId"`
	SourceType     string    `json:"sourceType"`
	SourceID       string    `json:"sourceId"`
	DeviceID       string    `json:"deviceId"`
	Channel        string    `json:"channel"`
	Quality        string    `json:"quality"`
	LiveKitURL     string    `json:"livekitUrl"`
	RoomName       string    `json:"roomName"`
	PublisherToken string    `json:"publisherToken"`
	RTSPURL        string    `json:"rtspUrl"`
	ExpiresAt      time.Time `json:"expiresAt"`
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
	SessionID string    `json:"sessionId"`
	Status    string    `json:"status"`
	TrackSid  string    `json:"trackSid,omitempty"`
	TrackName string    `json:"trackName,omitempty"`
	ErrorCode string    `json:"errorCode,omitempty"`
	Message   string    `json:"message,omitempty"`
	Timestamp time.Time `json:"timestamp"`
}

type FixedCameraGatewayStatus struct {
	Version    string    `json:"version"`
	GatewayID  string    `json:"gatewayId"`
	Status     string    `json:"status"`
	Sequence   uint64    `json:"sequence"`
	ReportedAt time.Time `json:"reportedAt"`
	ReasonCode string    `json:"reasonCode,omitempty"`
}

type FixedCameraHealthStatus struct {
	Version    string    `json:"version"`
	GatewayID  string    `json:"gatewayId"`
	CameraID   string    `json:"cameraId"`
	Health     string    `json:"health"`
	Sequence   uint64    `json:"sequence"`
	CheckedAt  time.Time `json:"checkedAt"`
	ReasonCode string    `json:"reasonCode,omitempty"`
}

type FixedCameraCatalogSnapshot struct {
	Version        string                     `json:"version"`
	GatewayID      string                     `json:"gatewayId"`
	CatalogVersion uint64                     `json:"catalogVersion"`
	IssuedAt       time.Time                  `json:"issuedAt"`
	Cameras        []FixedCameraCatalogRecord `json:"cameras"`
}

type FixedCameraCatalogRecord struct {
	CameraID      string    `json:"cameraId"`
	Enabled       bool      `json:"enabled"`
	ProtocolType  string    `json:"protocolType"`
	MainStreamURL string    `json:"mainStreamUrl,omitempty"`
	SubStreamURL  string    `json:"subStreamUrl,omitempty"`
	ExpiresAt     time.Time `json:"expiresAt"`
}

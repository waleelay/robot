package model

import "time"

type StartCommand struct {
	CommandID       string    `json:"commandId"`
	SessionID       string    `json:"sessionId"`
	RobotID         string    `json:"robotId"`
	SourceType      string    `json:"sourceType"`
	SourceID        string    `json:"sourceId"`
	DeviceID        string    `json:"deviceId"`
	Channel         string    `json:"channel"`
	Quality         string    `json:"quality"`
	LiveKitURL      string    `json:"livekitUrl"`
	RoomName        string    `json:"roomName"`
	PublisherToken  string    `json:"publisherToken"`
	PublishIdentity string    `json:"publishIdentity"`
	RTSPURL         string    `json:"rtspUrl"`
	ExpiresAt       time.Time `json:"expiresAt"`
}

type StopCommand struct {
	CommandID  string `json:"commandId"`
	SessionID  string `json:"sessionId"`
	SourceType string `json:"sourceType,omitempty"`
	SourceID   string `json:"sourceId,omitempty"`
	DeviceID   string `json:"deviceId,omitempty"`
	RoomName   string `json:"roomName"`
}

type IntercomStartCommand struct {
	CommandID              string    `json:"commandId"`
	SessionID              string    `json:"sessionId"`
	RobotID                string    `json:"robotId"`
	DeviceID               string    `json:"deviceId"`
	RoomName               string    `json:"roomName"`
	LiveKitURL             string    `json:"livekitUrl"`
	RobotToken             string    `json:"robotToken"`
	PublishAudio           bool      `json:"publishAudio"`
	SubscribeOperatorAudio bool      `json:"subscribeOperatorAudio"`
	PublishVideo           bool      `json:"publishVideo"`
	ExpiresAt              time.Time `json:"expiresAt"`
}

type IntercomStatusMessage struct {
	SessionID           string    `json:"sessionId"`
	Status              string    `json:"status"`
	RobotAudioTrackSid  string    `json:"robotAudioTrackSid,omitempty"`
	RobotAudioTrackName string    `json:"robotAudioTrackName,omitempty"`
	ErrorCode           string    `json:"errorCode,omitempty"`
	Message             string    `json:"message,omitempty"`
	Timestamp           time.Time `json:"timestamp"`
}

type IntercomCallInvite struct {
	CallID         string    `json:"callId"`
	RobotID        string    `json:"robotId"`
	DeviceID       string    `json:"deviceId"`
	Channel        string    `json:"channel"`
	Quality        string    `json:"quality"`
	Reason         string    `json:"reason,omitempty"`
	TimeoutSeconds int       `json:"timeoutSeconds"`
	Timestamp      time.Time `json:"timestamp"`
}

type IntercomCallCancel struct {
	CallID    string    `json:"callId"`
	RobotID   string    `json:"robotId"`
	Reason    string    `json:"reason,omitempty"`
	Timestamp time.Time `json:"timestamp"`
}

type IntercomCallState struct {
	CallID    string `json:"callId"`
	RobotID   string `json:"robotId"`
	Status    string `json:"status"`
	SessionID string `json:"sessionId,omitempty"`
	Message   string `json:"message,omitempty"`
	Timestamp string `json:"timestamp,omitempty"`
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

type OnlineMessage struct {
	Status  string   `json:"status"`
	Cameras []Camera `json:"cameras,omitempty"`
	Devices []Device `json:"devices,omitempty"`
}

type Camera struct {
	CameraID  string `json:"cameraId"`
	DeviceID  string `json:"deviceId"`
	GroupType string `json:"groupType"`
	Name      string `json:"name"`
	Quality   string `json:"quality"`
}

type Device struct {
	DeviceID     string         `json:"deviceId"`
	DeviceType   string         `json:"deviceType,omitempty"`
	OnlineStatus string         `json:"onlineStatus,omitempty"`
	Status       map[string]any `json:"status,omitempty"`
}

type ControlCommand struct {
	Protocol         string         `json:"protocol"`
	Version          string         `json:"version"`
	MessageType      string         `json:"messageType"`
	CommandID        string         `json:"commandId"`
	TraceID          string         `json:"traceId,omitempty"`
	RobotID          string         `json:"robotId"`
	ControlSessionID string         `json:"controlSessionId,omitempty"`
	ControlMode      string         `json:"controlMode,omitempty"`
	Target           ControlTarget  `json:"target"`
	Action           string         `json:"action"`
	Params           map[string]any `json:"params"`
	Policy           map[string]any `json:"policy,omitempty"`
	Seq              int64          `json:"seq,omitempty"`
	IssuedAt         string         `json:"issuedAt,omitempty"`
}

type ControlTarget struct {
	Scope      string `json:"scope"`
	DeviceID   string `json:"deviceId"`
	DeviceType string `json:"deviceType"`
	Vendor     string `json:"vendor,omitempty"`
	Model      string `json:"model,omitempty"`
}

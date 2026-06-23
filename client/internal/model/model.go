package model

import "time"

type StartCommand struct {
	CommandID       string    `json:"commandId"`
	SessionID       string    `json:"sessionId"`
	RobotID         string    `json:"robotId"`
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
	CommandID string `json:"commandId"`
	SessionID string `json:"sessionId"`
	RoomName  string `json:"roomName"`
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
	RobotID          string    `json:"robotId"`
	ClientID         string    `json:"clientId"`
	Name             string    `json:"name"`
	Type             string    `json:"type"`
	Battery          int       `json:"battery"`
	Status           string    `json:"status"`
	ControlMode      string    `json:"controlMode"`
	StateSeq         int64     `json:"stateSeq"`
	MissionStatus    string    `json:"missionStatus"`
	NavigationStatus string    `json:"navigationStatus"`
	ControlOwner     any       `json:"controlOwner"`
	EstopActive      bool      `json:"estopActive"`
	Cameras          []Camera  `json:"cameras,omitempty"`
	Timestamp        time.Time `json:"timestamp"`
}

type Camera struct {
	CameraID  string `json:"cameraId"`
	DeviceID  string `json:"deviceId"`
	GroupType string `json:"groupType"`
	Name      string `json:"name"`
	Quality   string `json:"quality"`
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

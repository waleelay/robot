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

type AckMessage struct {
	CommandID string    `json:"commandId"`
	SessionID string    `json:"sessionId"`
	Success   bool      `json:"success"`
	Message   string    `json:"message"`
	Timestamp time.Time `json:"timestamp"`
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
	RobotID   string    `json:"robotId"`
	ClientID  string    `json:"clientId"`
	Status    string    `json:"status"`
	Timestamp time.Time `json:"timestamp"`
}

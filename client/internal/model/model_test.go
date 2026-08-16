package model

import (
	"encoding/json"
	"testing"
	"time"
)

func TestOnlineMessageContainsOnlyMediaClientFields(t *testing.T) {
	body, err := json.Marshal(OnlineMessage{
		RobotID:   "robot-001",
		ClientID:  "media-client-001",
		Status:    "online",
		Cameras:   []Camera{},
		Devices:   []Device{},
		Timestamp: time.Now(),
	})
	if err != nil {
		t.Fatal(err)
	}

	var payload map[string]any
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatal(err)
	}
	for _, field := range []string{
		"name", "type", "battery", "controlMode", "stateSeq", "missionStatus",
		"navigationStatus", "controlOwner", "estopActive",
	} {
		if _, exists := payload[field]; exists {
			t.Fatalf("media client status must not contain %q", field)
		}
	}
}

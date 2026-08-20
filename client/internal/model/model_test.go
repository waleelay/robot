package model

import (
	"encoding/json"
	"testing"
)

func TestOnlineMessageContainsOnlyMediaClientFields(t *testing.T) {
	body, err := json.Marshal(OnlineMessage{
		Status:  "online",
		Cameras: []Camera{},
		Devices: []Device{},
	})
	if err != nil {
		t.Fatal(err)
	}

	var payload map[string]any
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Fatal(err)
	}
	for _, field := range []string{
		"robotId", "clientId", "timestamp", "name", "type", "battery", "controlMode",
		"stateSeq", "missionStatus", "navigationStatus", "controlOwner", "estopActive",
	} {
		if _, exists := payload[field]; exists {
			t.Fatalf("media client status must not contain %q", field)
		}
	}
}

package multifunction

import (
	"bytes"
	"testing"
)

func TestBuildLightFrameMatchesCapturedPackets(t *testing.T) {
	tests := []struct {
		name      string
		messageID byte
		payload   []byte
		want      []byte
	}{
		{name: "keepalive", messageID: lightKeepalive, want: []byte{0x8D, 0x00, 0x04, 0x61}},
		{name: "power on", messageID: lightPower, payload: []byte{0x01}, want: []byte{0x8D, 0x01, 0x01, 0x01, 0x31}},
		{name: "power off", messageID: lightPower, payload: []byte{0x00}, want: []byte{0x8D, 0x01, 0x01, 0x00, 0x6F}},
		{name: "strobe on", messageID: lightStrobe, payload: []byte{0x01}, want: []byte{0x8D, 0x01, 0x03, 0x01, 0xA0}},
		{name: "red blue mode 2", messageID: lightRedBlueMode, payload: []byte{0x02}, want: []byte{0x8D, 0x01, 0x07, 0x02, 0x79}},
		{name: "light tilt", messageID: lightTilt, payload: []byte{0xFF, 0xA2}, want: []byte{0x8D, 0x02, 0x09, 0xFF, 0xA2, 0x1B}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := BuildLightFrame(test.messageID, test.payload...); !bytes.Equal(got, test.want) {
				t.Fatalf("frame mismatch: got=% X want=% X", got, test.want)
			}
		})
	}
}

func TestProtocolValueMappings(t *testing.T) {
	if got := volumeCommand(47); string(got) != "[14]2f" {
		t.Fatalf("unexpected volume command: %q", got)
	}
	if got := percentToBrightnessRaw(47); got != 14 {
		t.Fatalf("unexpected brightness raw value: %d", got)
	}
	if got := percentToSpeakerTiltRaw(0); got != 80 {
		t.Fatalf("unexpected speaker minimum: %d", got)
	}
	if got := percentToSpeakerTiltRaw(100); got != 220 {
		t.Fatalf("unexpected speaker maximum: %d", got)
	}
	if got := percentToLightTiltRaw(0); got != 100 {
		t.Fatalf("unexpected light minimum: %d", got)
	}
	if got := percentToLightTiltRaw(100); got != 200 {
		t.Fatalf("unexpected light maximum: %d", got)
	}
}

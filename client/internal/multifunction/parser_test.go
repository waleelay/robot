package multifunction

import (
	"bytes"
	"testing"
)

func TestStreamParserHandlesFragmentedAndCoalescedMessages(t *testing.T) {
	parser := &streamParser{}
	if events := parser.feed([]byte(`[99]"{"volume_real": 3`)); len(events) != 0 {
		t.Fatalf("fragment should not produce events: %#v", events)
	}
	events := parser.feed([]byte(`0, "volume_limit": 100, "temperature": "48.49"}"[14]"1e"[30]`))
	if len(events) != 3 {
		t.Fatalf("unexpected event count: %d", len(events))
	}
	if events[0].kind != "status" {
		t.Fatalf("unexpected first event: %#v", events[0])
	}
	status, err := parseDeviceStatus(events[0].payload)
	if err != nil {
		t.Fatal(err)
	}
	if status["volumePercent"] != 30 || status["volumeLimitPercent"] != 100 || status["temperatureC"] != 48.49 {
		t.Fatalf("unexpected status: %#v", status)
	}
	if events[1].kind != "volume" || string(events[1].payload) != "1e" {
		t.Fatalf("unexpected volume event: %#v", events[1])
	}
	if events[2].kind != "tts_finished" {
		t.Fatalf("unexpected completion event: %#v", events[2])
	}
}

func TestStreamParserExtractsMonitorFrameBeforeNextMarker(t *testing.T) {
	parser := &streamParser{}
	events := parser.feed(append([]byte("[40]\x01\x02\x03"), []byte("[39]")...))
	if len(events) != 2 {
		t.Fatalf("unexpected event count: %d", len(events))
	}
	if events[0].kind != "monitor_opus" || !bytes.Equal(events[0].payload, []byte{1, 2, 3}) {
		t.Fatalf("unexpected monitor frame: %#v", events[0])
	}
	if events[1].kind != "audio_finished" {
		t.Fatalf("unexpected second event: %#v", events[1])
	}
}

func TestStreamParserAcceptsSingleDigitVolumeAndContinues(t *testing.T) {
	parser := &streamParser{}
	events := parser.feed([]byte(`[14]"a"[99]"{"volume_real": 10}"`))
	if len(events) != 2 {
		t.Fatalf("unexpected event count: %d", len(events))
	}
	if events[0].kind != "volume" || string(events[0].payload) != "a" {
		t.Fatalf("unexpected volume event: %#v", events[0])
	}
	status, err := parseDeviceStatus(events[1].payload)
	if err != nil {
		t.Fatal(err)
	}
	if status["volumePercent"] != 10 {
		t.Fatalf("unexpected status: %#v", status)
	}
}

func TestStreamParserDoesNotSplitMonitorFrameOnRawLightHeader(t *testing.T) {
	parser := &streamParser{}
	opus := []byte{0x48, 0x8D, 0x01, 0x02, 0x03, 0x7F}
	input := append(append([]byte("[40]"), opus...), []byte("[39]")...)
	events := parser.feed(input)
	if len(events) != 2 {
		t.Fatalf("unexpected event count: %d", len(events))
	}
	if events[0].kind != "monitor_opus" || !bytes.Equal(events[0].payload, opus) {
		t.Fatalf("unexpected monitor event: %#v", events[0])
	}
}

func TestStreamParserFindsValidatedLightFrameAfterMonitorFrame(t *testing.T) {
	parser := &streamParser{}
	light := BuildLightFrame(lightKeepalive)
	input := append([]byte("[40]\x48\x01"), light...)
	input = append(input, []byte("[30]")...)
	events := parser.feed(input)
	if len(events) != 3 {
		t.Fatalf("unexpected event count: %d", len(events))
	}
	if events[0].kind != "monitor_opus" || !bytes.Equal(events[0].payload, []byte{0x48, 0x01}) {
		t.Fatalf("unexpected monitor event: %#v", events[0])
	}
	if events[1].kind != "light_frame" || !bytes.Equal(events[1].payload, light) {
		t.Fatalf("unexpected light event: %#v", events[1])
	}
}

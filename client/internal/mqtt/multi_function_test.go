package mqtt

import (
	"context"
	"errors"
	"testing"

	"robot-media-client/internal/config"
	"robot-media-client/internal/model"
)

type fakeMultiFunctionAdapter struct {
	action string
	params map[string]any
	state  map[string]any
	err    error
}

func (f *fakeMultiFunctionAdapter) Start(context.Context) {}
func (f *fakeMultiFunctionAdapter) Execute(_ context.Context, action string, params map[string]any) (map[string]any, error) {
	f.action = action
	f.params = params
	return f.state, f.err
}
func (f *fakeMultiFunctionAdapter) WriteBroadcastOpusFrame(context.Context, []byte) error {
	return nil
}
func (f *fakeMultiFunctionAdapter) SetStateHandler(func(map[string]any)) {}
func (f *fakeMultiFunctionAdapter) SetMonitorFrameHandler(func([]byte))  {}
func (f *fakeMultiFunctionAdapter) Snapshot() map[string]any             { return f.state }
func (f *fakeMultiFunctionAdapter) Close() error                         { return nil }

func TestApplyMultiFunctionCommandUsesRealAdapterState(t *testing.T) {
	adapter := &fakeMultiFunctionAdapter{
		state: map[string]any{
			"connected":     true,
			"volumePercent": 37,
		},
	}
	client := &Client{
		cfg: config.Config{
			MultiFunction: config.MultiFunctionConfig{DeviceID: "broadcaster-001"},
		},
		multiFunc:   adapter,
		deviceState: make(map[string]map[string]any),
		audioVolume: 50,
	}
	target := model.ControlTarget{
		DeviceID:   "broadcaster-001",
		DeviceType: "MULTI_FUNCTION_BROADCASTER",
	}

	if !client.applyControlCommand(context.Background(), model.ControlCommand{
		Target: target,
		Action: "set_volume",
		Params: map[string]any{"volumePercent": 37},
	}) {
		t.Fatal("set_volume should be handled")
	}
	if adapter.action != "set_volume" || anyInt(adapter.params["volumePercent"], -1) != 37 {
		t.Fatalf("command was not sent to adapter: action=%s params=%#v", adapter.action, adapter.params)
	}
	if state := client.deviceState["broadcaster-001"]; state["connected"] != true || state["volumePercent"] != 37 {
		t.Fatalf("adapter state was not copied: %#v", state)
	}

	adapter.err = errors.New("device write failed")
	adapter.state = map[string]any{"connected": false, "lastError": adapter.err.Error()}
	if !client.applyControlCommand(context.Background(), model.ControlCommand{
		Target: target,
		Action: "light.set",
		Params: map[string]any{"enabled": true},
	}) {
		t.Fatal("failed adapter command should still be consumed")
	}
	if state := client.deviceState["broadcaster-001"]; state["connected"] != false {
		t.Fatalf("failed adapter state was not reported: %#v", state)
	}
}

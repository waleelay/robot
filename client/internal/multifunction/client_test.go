package multifunction

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"sync"
	"testing"
	"time"

	"robot-media-client/internal/config"
)

func TestClientExecutesCapturedTCPAndHTTPProtocols(t *testing.T) {
	controlListener := mustListen(t)
	tiltListener := mustListen(t)
	controlWrites := make(chan []byte, 16)
	go serveControlConnection(controlListener, controlWrites)
	tiltWrite := make(chan []byte, 1)
	go serveSingleWrite(tiltListener, tiltWrite)

	var deleteMu sync.Mutex
	deletedFile := ""
	uploadedFile := ""
	uploadedBody := ""
	httpServer := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		switch request.URL.Path {
		case "/api/media/files/file-001/content":
			if request.Header.Get("X-Org-Id") != "org001" {
				http.Error(writer, "missing org", http.StatusForbidden)
				return
			}
			_, _ = writer.Write([]byte("safe-transfer-audio"))
		case "/fetch-files":
			writer.Header().Set("Content-Type", "application/json")
			_, _ = writer.Write([]byte(`{"code":0,"data":["notice.mp3","alarm.wav"]}`))
		case "/del-file":
			_ = request.ParseForm()
			deleteMu.Lock()
			deletedFile = request.Form.Get("filename")
			deleteMu.Unlock()
			_, _ = writer.Write([]byte(`{"code":0}`))
		case "/upload-file":
			_ = request.ParseMultipartForm(1 << 20)
			file, header, openErr := request.FormFile("file")
			if openErr != nil {
				http.Error(writer, openErr.Error(), http.StatusBadRequest)
				return
			}
			defer file.Close()
			body, _ := io.ReadAll(file)
			deleteMu.Lock()
			uploadedFile = header.Filename
			uploadedBody = string(body)
			deleteMu.Unlock()
			_, _ = writer.Write([]byte(`{"code":0}`))
		default:
			http.NotFound(writer, request)
		}
	}))
	defer httpServer.Close()

	httpURL, err := url.Parse(httpServer.URL)
	if err != nil {
		t.Fatal(err)
	}
	httpPort, _ := strconv.Atoi(httpURL.Port())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	client := New(config.MultiFunctionConfig{
		Enabled:           true,
		DeviceID:          "broadcaster-001",
		MediaServiceURL:   httpServer.URL,
		Host:              "127.0.0.1",
		ControlPort:       listenerPort(controlListener),
		TiltPort:          listenerPort(tiltListener),
		HTTPPort:          httpPort,
		DialTimeout:       time.Second,
		WriteTimeout:      time.Second,
		HTTPTimeout:       time.Second,
		KeepaliveEnabled:  false,
		KeepaliveInterval: time.Second,
	})
	client.Start(ctx)
	defer client.Close()

	waitFor(t, time.Second, func() bool {
		return boolValue(client.Snapshot()["connected"], false)
	})
	waitFor(t, time.Second, func() bool {
		return intValue(client.Snapshot()["volumePercent"], -1) == 30
	})

	commandContext, commandCancel := context.WithTimeout(ctx, 2*time.Second)
	defer commandCancel()
	if _, err := client.Execute(commandContext, "set_volume", map[string]any{"volumePercent": 47}); err != nil {
		t.Fatal(err)
	}
	if _, err := client.Execute(commandContext, "light.set", map[string]any{
		"enabled":       true,
		"brightness":    47,
		"strobeEnabled": true,
		"redBlueMode":   2,
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := client.Execute(commandContext, "set_light_tilt", map[string]any{"positionPercent": 62}); err != nil {
		t.Fatal(err)
	}
	if _, err := client.Execute(commandContext, "set_speaker_tilt", map[string]any{"positionPercent": 50}); err != nil {
		t.Fatal(err)
	}

	wantControl := bytes.Join([][]byte{
		[]byte("[14]2f"),
		BuildLightFrame(lightPower, 1),
		BuildLightFrame(lightBrightness, 14),
		BuildLightFrame(lightStrobe, 1),
		BuildLightFrame(lightRedBlueMode, 2),
		BuildLightFrame(lightTilt, 0xFF, 0xA2),
	}, nil)
	gotControl := collectUntil(t, controlWrites, wantControl)
	if !bytes.Contains(gotControl, wantControl) {
		t.Fatalf("control writes mismatch:\n got=% X\nwant=% X", gotControl, wantControl)
	}
	if _, err := client.Execute(commandContext, "play_audio_file", map[string]any{
		"fileName": "notice.mp3",
		"loop":     true,
	}); err != nil {
		t.Fatal(err)
	}
	playback := mapValue(client.Snapshot()["audioPlayback"])
	if !boolValue(playback["playing"], false) ||
		stringParam(playback, "fileName") != "notice.mp3" ||
		!boolValue(playback["loop"], false) {
		t.Fatalf("unexpected playback state: %#v", playback)
	}
	if _, err := client.Execute(commandContext, "stop_audio_file", map[string]any{}); err != nil {
		t.Fatal(err)
	}
	if boolValue(mapValue(client.Snapshot()["audioPlayback"])["playing"], true) {
		t.Fatal("playback should be stopped")
	}
	select {
	case got := <-tiltWrite:
		if !bytes.Equal(got, []byte{0x8D, 0x96}) {
			t.Fatalf("speaker tilt mismatch: got=% X want=8D 96", got)
		}
	case <-time.After(time.Second):
		t.Fatal("speaker tilt command not received")
	}

	files, err := client.ListAudioFiles(commandContext)
	if err != nil {
		t.Fatal(err)
	}
	if fmt.Sprint(files) != "[notice.mp3 alarm.wav]" {
		t.Fatalf("unexpected audio files: %#v", files)
	}
	if err := client.DeleteAudioFile(commandContext, "notice.mp3"); err != nil {
		t.Fatal(err)
	}
	transferBody := []byte("safe-transfer-audio")
	if _, err := client.Execute(commandContext, "upload_audio_file", map[string]any{
		"transferId": "mat-test-001",
		"fileId":     "file-001",
		"fileName":   "remote-notice.mp3",
		"fileSize":   len(transferBody),
		"orgId":      "org001",
	}); err != nil {
		t.Fatal(err)
	}
	deleteMu.Lock()
	if uploadedFile != "remote-notice.mp3" || uploadedBody != string(transferBody) {
		t.Fatalf("unexpected transferred file: name=%q body=%q", uploadedFile, uploadedBody)
	}
	deleteMu.Unlock()
	transferState := mapValue(client.Snapshot()["audioTransfer"])
	if stringParam(transferState, "status") != "COMPLETED" {
		t.Fatalf("unexpected transfer state: %#v", transferState)
	}
	uploadPath := filepath.Join(t.TempDir(), "field-test.wav")
	if err := os.WriteFile(uploadPath, []byte("safe-test-audio"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := client.UploadAudioFile(commandContext, uploadPath); err != nil {
		t.Fatal(err)
	}
	deleteMu.Lock()
	defer deleteMu.Unlock()
	if deletedFile != "notice.mp3" {
		t.Fatalf("unexpected deleted file: %q", deletedFile)
	}
	if uploadedFile != "field-test.wav" || uploadedBody != "safe-test-audio" {
		t.Fatalf("unexpected uploaded file: name=%q body=%q", uploadedFile, uploadedBody)
	}
}

func TestClientRejectsInvalidParametersBeforeWriting(t *testing.T) {
	client := New(config.MultiFunctionConfig{Enabled: true})
	if _, err := client.Execute(context.Background(), "set_volume", map[string]any{"volumePercent": 101}); err == nil {
		t.Fatal("out-of-range volume should fail")
	}
	if _, err := client.Execute(context.Background(), "play_tts", map[string]any{"text": "", "voice": "MALE"}); err == nil {
		t.Fatal("empty TTS text should fail")
	}
	if _, err := client.Execute(context.Background(), "light.set", map[string]any{}); err == nil {
		t.Fatal("empty light command should fail")
	}
	if _, err := client.Execute(context.Background(), "play_audio_file", map[string]any{"fileName": "../bad.mp3"}); err == nil {
		t.Fatal("path traversal should fail")
	}
}

func mustListen(t *testing.T) net.Listener {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	return listener
}

func listenerPort(listener net.Listener) int {
	return listener.Addr().(*net.TCPAddr).Port
}

func serveControlConnection(listener net.Listener, writes chan<- []byte) {
	conn, err := listener.Accept()
	if err != nil {
		return
	}
	defer conn.Close()
	_, _ = conn.Write([]byte(`[99]"{"volume_real": 30, "volume_limit": 100, "temperature": "48.49"}"`))
	buffer := make([]byte, 4096)
	for {
		count, readErr := conn.Read(buffer)
		if count > 0 {
			writes <- append([]byte(nil), buffer[:count]...)
		}
		if readErr != nil {
			return
		}
	}
}

func serveSingleWrite(listener net.Listener, write chan<- []byte) {
	conn, err := listener.Accept()
	if err != nil {
		return
	}
	defer conn.Close()
	payload, _ := io.ReadAll(conn)
	write <- payload
}

func collectUntil(t *testing.T, chunks <-chan []byte, want []byte) []byte {
	t.Helper()
	deadline := time.After(2 * time.Second)
	var all []byte
	for {
		select {
		case chunk := <-chunks:
			all = append(all, chunk...)
			if bytes.Contains(all, want) {
				return all
			}
		case <-deadline:
			return all
		}
	}
}

func waitFor(t *testing.T, timeout time.Duration, condition func() bool) {
	t.Helper()
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if condition() {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("condition was not met before timeout")
}

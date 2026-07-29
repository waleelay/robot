package multifunction

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"mime/multipart"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"robot-media-client/internal/config"
)

var ErrDisabled = errors.New("multi-function device adapter is disabled")

type Adapter interface {
	Start(context.Context)
	Execute(context.Context, string, map[string]any) (map[string]any, error)
	WriteBroadcastOpusFrame(context.Context, []byte) error
	SetStateHandler(func(map[string]any))
	SetMonitorFrameHandler(func([]byte))
	Snapshot() map[string]any
	Close() error
}

type Client struct {
	cfg config.MultiFunctionConfig

	ctx    context.Context
	cancel context.CancelFunc

	connMu          sync.RWMutex
	conn            net.Conn
	connectedSignal chan struct{}
	writeMu         sync.Mutex

	stateMu        sync.RWMutex
	state          map[string]any
	stateHandler   func(map[string]any)
	monitorHandler func([]byte)

	startOnce sync.Once
	closeOnce sync.Once
	wg        sync.WaitGroup
}

func New(cfg config.MultiFunctionConfig) *Client {
	return &Client{
		cfg:             cfg,
		connectedSignal: make(chan struct{}),
		state: map[string]any{
			"connected":  false,
			"audioFiles": []string{},
			"audioSession": map[string]any{
				"mediaSessionId":    "",
				"state":             "IDLE",
				"broadcastActive":   false,
				"monitorActive":     false,
				"monitorSuppressed": false,
				"monitorTrackSid":   "",
				"lastError":         nil,
			},
		},
	}
}

func (c *Client) Start(parent context.Context) {
	c.startOnce.Do(func() {
		if !c.cfg.Enabled {
			c.updateState(map[string]any{"connected": false, "lastError": ErrDisabled.Error()})
			return
		}
		c.ctx, c.cancel = context.WithCancel(parent)
		c.wg.Add(1)
		go c.connectionLoop()
		if c.cfg.KeepaliveEnabled {
			c.wg.Add(1)
			go c.keepaliveLoop()
		}
	})
}

func (c *Client) Close() error {
	c.closeOnce.Do(func() {
		if c.cancel != nil {
			c.cancel()
		}
		c.closeConnection(nil)
		c.wg.Wait()
	})
	return nil
}

func (c *Client) SetStateHandler(handler func(map[string]any)) {
	c.stateMu.Lock()
	c.stateHandler = handler
	c.stateMu.Unlock()
}

func (c *Client) SetMonitorFrameHandler(handler func([]byte)) {
	c.stateMu.Lock()
	c.monitorHandler = handler
	c.stateMu.Unlock()
}

func (c *Client) Snapshot() map[string]any {
	c.stateMu.RLock()
	defer c.stateMu.RUnlock()
	return cloneMap(c.state)
}

func (c *Client) Execute(ctx context.Context, action string, params map[string]any) (map[string]any, error) {
	if !c.cfg.Enabled {
		return c.Snapshot(), ErrDisabled
	}
	var err error
	switch action {
	case "set_volume":
		percent, valueErr := requiredPercent(params, "volumePercent")
		if valueErr != nil {
			err = valueErr
			break
		}
		err = c.sendControl(ctx, volumeCommand(percent))
	case "start_broadcast":
		err = c.ensureConnected(ctx)
		if err == nil {
			c.updateAudioSession(stringParam(params, "mediaSessionId"), map[string]any{"broadcastActive": true})
		}
	case "stop_broadcast":
		err = c.sendControl(ctx, []byte("[11]"))
		if err == nil {
			c.updateAudioSession("", map[string]any{"broadcastActive": false})
		}
	case "start_monitor":
		err = c.sendControl(ctx, []byte("[40]"))
		if err == nil {
			c.updateAudioSession(stringParam(params, "mediaSessionId"), map[string]any{"monitorActive": true})
		}
	case "stop_monitor":
		err = c.sendControl(ctx, []byte("[41]"))
		if err == nil {
			c.updateAudioSession("", map[string]any{"monitorActive": false, "monitorSuppressed": false})
		}
	case "set_monitor_suppressed":
		suppressed := boolParam(params, "suppressed", false)
		command := []byte("[46]")
		if suppressed {
			command = []byte("[45]")
		}
		err = c.sendControl(ctx, command)
		if err == nil {
			c.updateAudioSession("", map[string]any{"monitorSuppressed": suppressed})
		}
	case "play_tts":
		text := stringParam(params, "text")
		voice := strings.ToUpper(stringParam(params, "voice"))
		if text == "" {
			err = errors.New("text is required")
		} else if voice != "MALE" && voice != "FEMALE" {
			err = errors.New("voice must be MALE or FEMALE")
		} else {
			err = c.sendControl(ctx, ttsCommand(text, voice, boolParam(params, "loop", false)))
		}
	case "stop_tts":
		err = c.sendControl(ctx, []byte("[17]"))
	case "list_audio_files":
		var files []string
		files, err = c.ListAudioFiles(ctx)
		if err == nil {
			c.updateState(map[string]any{
				"audioFiles":          files,
				"audioFilesUpdatedAt": time.Now().Format(time.RFC3339Nano),
			})
		}
	case "play_audio_file":
		fileName := stringParam(params, "fileName")
		if err = validateFileName(fileName); err == nil {
			err = c.sendControl(ctx, audioFileCommand(fileName, boolParam(params, "loop", false)))
		}
	case "stop_audio_file":
		err = c.sendControl(ctx, []byte("[13]"))
	case "delete_audio_file":
		err = c.DeleteAudioFile(ctx, stringParam(params, "fileName"))
		if err == nil {
			if files, listErr := c.ListAudioFiles(ctx); listErr == nil {
				c.updateState(map[string]any{
					"audioFiles":          files,
					"audioFilesUpdatedAt": time.Now().Format(time.RFC3339Nano),
				})
			}
		}
	case "play_alarm":
		err = c.sendControl(ctx, []byte("[18]"))
	case "stop_alarm":
		err = c.sendControl(ctx, []byte("[19]"))
	case "light.set":
		err = c.setLight(ctx, params)
	case "set_speaker_tilt":
		var percent int
		percent, err = requiredPercent(params, "positionPercent")
		if err == nil {
			err = c.setSpeakerTilt(ctx, percent)
		}
	case "set_light_tilt":
		var percent int
		percent, err = requiredPercent(params, "positionPercent")
		if err == nil {
			err = c.sendControl(ctx, BuildLightFrame(lightTilt, 0xFF, percentToLightTiltRaw(percent)))
		}
	default:
		err = fmt.Errorf("unsupported multi-function action: %s", action)
	}
	if err != nil {
		c.updateState(map[string]any{"lastError": err.Error()})
		return c.Snapshot(), err
	}
	c.updateState(map[string]any{"lastError": nil, "lastCommand": action, "lastCommandAt": time.Now().Format(time.RFC3339Nano)})
	return c.Snapshot(), nil
}

func (c *Client) WriteBroadcastOpusFrame(ctx context.Context, opusFrame []byte) error {
	if len(opusFrame) == 0 {
		return errors.New("empty broadcast Opus frame")
	}
	session := mapValue(c.Snapshot()["audioSession"])
	if !boolValue(session["broadcastActive"], false) {
		return errors.New("broadcast is not active")
	}
	payload := make([]byte, 0, len(opusFrame)+4)
	payload = append(payload, []byte("[10]")...)
	payload = append(payload, opusFrame...)
	return c.sendControl(ctx, payload)
}

func (c *Client) ListAudioFiles(ctx context.Context) ([]string, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, c.httpURL("/fetch-files"), nil)
	if err != nil {
		return nil, err
	}
	response, err := c.httpClient().Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(io.LimitReader(response.Body, 4<<20))
	if err != nil {
		return nil, err
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, fmt.Errorf("fetch files failed status=%d body=%s", response.StatusCode, strings.TrimSpace(string(body)))
	}
	if err := ensureDeviceHTTPSuccess(body); err != nil {
		return nil, err
	}
	files := extractFileNames(body)
	return files, nil
}

func (c *Client) DeleteAudioFile(ctx context.Context, fileName string) error {
	if err := validateFileName(fileName); err != nil {
		return err
	}
	form := url.Values{"filename": []string{fileName}}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, c.httpURL("/del-file"), strings.NewReader(form.Encode()))
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	response, err := c.httpClient().Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(response.Body, 1<<20))
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf("delete file failed status=%d body=%s", response.StatusCode, strings.TrimSpace(string(body)))
	}
	if err := ensureDeviceHTTPSuccess(body); err != nil {
		return err
	}
	return nil
}

func (c *Client) UploadAudioFile(ctx context.Context, path string) error {
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	defer file.Close()
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	part, err := writer.CreateFormFile("file", filepath.Base(path))
	if err != nil {
		return err
	}
	if _, err := io.Copy(part, file); err != nil {
		return err
	}
	if err := writer.Close(); err != nil {
		return err
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, c.httpURL("/upload-file"), &body)
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", writer.FormDataContentType())
	response, err := c.httpClient().Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	responseBody, _ := io.ReadAll(io.LimitReader(response.Body, 1<<20))
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf("upload file failed status=%d body=%s", response.StatusCode, strings.TrimSpace(string(responseBody)))
	}
	if err := ensureDeviceHTTPSuccess(responseBody); err != nil {
		return err
	}
	return nil
}

func (c *Client) connectionLoop() {
	defer c.wg.Done()
	backoff := time.Second
	for c.ctx.Err() == nil {
		address := net.JoinHostPort(c.cfg.Host, strconv.Itoa(c.cfg.ControlPort))
		dialer := net.Dialer{Timeout: c.cfg.DialTimeout}
		conn, err := dialer.DialContext(c.ctx, "tcp", address)
		if err != nil {
			c.updateState(map[string]any{"connected": false, "lastError": err.Error()})
			if !sleepContext(c.ctx, backoff) {
				return
			}
			if backoff < 5*time.Second {
				backoff *= 2
			}
			continue
		}
		backoff = time.Second
		c.installConnection(conn)
		log.Printf("multi-function connected address=%s", address)
		c.readLoop(conn)
		c.closeConnection(conn)
	}
}

func (c *Client) installConnection(conn net.Conn) {
	c.connMu.Lock()
	c.conn = conn
	close(c.connectedSignal)
	c.connMu.Unlock()
	c.updateState(map[string]any{"connected": true, "lastError": nil})
}

func (c *Client) closeConnection(expected net.Conn) {
	c.connMu.Lock()
	if expected == nil || c.conn == expected {
		if c.conn != nil {
			_ = c.conn.Close()
		}
		c.conn = nil
		c.connectedSignal = make(chan struct{})
		c.connMu.Unlock()
		c.updateState(map[string]any{"connected": false})
		return
	}
	c.connMu.Unlock()
}

func (c *Client) readLoop(conn net.Conn) {
	parser := &streamParser{}
	buffer := make([]byte, 16*1024)
	for c.ctx.Err() == nil {
		count, err := conn.Read(buffer)
		if count > 0 {
			for _, event := range parser.feed(buffer[:count]) {
				c.handleStreamEvent(event)
			}
		}
		if err != nil {
			if c.ctx.Err() == nil && !errors.Is(err, io.EOF) {
				c.updateState(map[string]any{"lastError": err.Error()})
			}
			return
		}
	}
}

func (c *Client) handleStreamEvent(event streamEvent) {
	switch event.kind {
	case "status":
		status, err := parseDeviceStatus(event.payload)
		if err != nil {
			c.updateState(map[string]any{"lastError": err.Error()})
			return
		}
		status["connected"] = true
		status["observedAt"] = time.Now().Format(time.RFC3339Nano)
		c.updateState(status)
	case "volume":
		if value, err := strconv.ParseInt(string(event.payload), 16, 32); err == nil {
			c.updateState(map[string]any{"volumePercent": clamp(int(value), 0, 100)})
		}
	case "tts_finished":
		c.updateState(map[string]any{"lastTtsCompletedAt": time.Now().Format(time.RFC3339Nano)})
	case "audio_finished":
		c.updateState(map[string]any{"lastAudioFinishedAt": time.Now().Format(time.RFC3339Nano)})
	case "monitor_opus":
		c.stateMu.RLock()
		handler := c.monitorHandler
		c.stateMu.RUnlock()
		if handler != nil {
			handler(append([]byte(nil), event.payload...))
		}
	}
}

func (c *Client) keepaliveLoop() {
	defer c.wg.Done()
	interval := c.cfg.KeepaliveInterval
	if interval <= 0 {
		interval = 2 * time.Second
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-c.ctx.Done():
			return
		case <-ticker.C:
			timeout, cancel := context.WithTimeout(c.ctx, c.cfg.WriteTimeout)
			_ = c.sendControl(timeout, keepaliveFrame())
			cancel()
		}
	}
}

func (c *Client) ensureConnected(ctx context.Context) error {
	_, err := c.waitConnection(ctx)
	return err
}

func (c *Client) waitConnection(ctx context.Context) (net.Conn, error) {
	if !c.cfg.Enabled {
		return nil, ErrDisabled
	}
	for {
		c.connMu.RLock()
		conn := c.conn
		signal := c.connectedSignal
		c.connMu.RUnlock()
		if conn != nil {
			return conn, nil
		}
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case <-signal:
		}
	}
}

func (c *Client) sendControl(ctx context.Context, payload []byte) error {
	conn, err := c.waitConnection(ctx)
	if err != nil {
		return err
	}
	c.writeMu.Lock()
	defer c.writeMu.Unlock()
	deadline := time.Now().Add(c.cfg.WriteTimeout)
	if value, ok := ctx.Deadline(); ok && value.Before(deadline) {
		deadline = value
	}
	if err := conn.SetWriteDeadline(deadline); err != nil {
		return err
	}
	for len(payload) > 0 {
		count, writeErr := conn.Write(payload)
		if writeErr != nil {
			c.closeConnection(conn)
			return writeErr
		}
		payload = payload[count:]
	}
	return nil
}

func (c *Client) setSpeakerTilt(ctx context.Context, percent int) error {
	address := net.JoinHostPort(c.cfg.Host, strconv.Itoa(c.cfg.TiltPort))
	dialer := net.Dialer{Timeout: c.cfg.DialTimeout}
	conn, err := dialer.DialContext(ctx, "tcp", address)
	if err != nil {
		return err
	}
	defer conn.Close()
	deadline := time.Now().Add(c.cfg.WriteTimeout)
	_ = conn.SetWriteDeadline(deadline)
	_, err = conn.Write([]byte{lightHeader, percentToSpeakerTiltRaw(percent)})
	return err
}

func (c *Client) setLight(ctx context.Context, params map[string]any) error {
	if _, hasEnabled := params["enabled"]; !hasEnabled {
		if _, hasBrightness := params["brightness"]; !hasBrightness {
			if _, hasStrobe := params["strobeEnabled"]; !hasStrobe {
				if _, hasRedBlue := params["redBlueMode"]; !hasRedBlue {
					return errors.New("light.set requires at least one parameter")
				}
			}
		}
	}
	if value, exists := params["enabled"]; exists {
		enabled, ok := strictBool(value)
		if !ok {
			return errors.New("enabled must be boolean")
		}
		if !enabled {
			if err := c.sendControl(ctx, BuildLightFrame(lightStrobe, 0)); err != nil {
				return err
			}
		}
		power := byte(0)
		if enabled {
			power = 1
		}
		if err := c.sendControl(ctx, BuildLightFrame(lightPower, power)); err != nil {
			return err
		}
	}
	if value, exists := params["brightness"]; exists {
		brightness, ok := strictInt(value)
		if !ok || brightness < 0 || brightness > 100 {
			return errors.New("brightness must be an integer between 0 and 100")
		}
		if err := c.sendControl(ctx, BuildLightFrame(lightBrightness, percentToBrightnessRaw(brightness))); err != nil {
			return err
		}
	}
	if value, exists := params["strobeEnabled"]; exists {
		enabled, ok := strictBool(value)
		if !ok {
			return errors.New("strobeEnabled must be boolean")
		}
		strobe := byte(0)
		if enabled {
			strobe = 1
		}
		if err := c.sendControl(ctx, BuildLightFrame(lightStrobe, strobe)); err != nil {
			return err
		}
		if !enabled {
			if err := c.sendControl(ctx, BuildLightFrame(lightPower, 0)); err != nil {
				return err
			}
		}
	}
	if value, exists := params["redBlueMode"]; exists {
		modeValue, ok := strictInt(value)
		if !ok || modeValue < 0 || modeValue > 16 {
			return errors.New("redBlueMode must be an integer between 0 and 16")
		}
		mode := byte(modeValue)
		if err := c.sendControl(ctx, BuildLightFrame(lightRedBlueMode, mode)); err != nil {
			return err
		}
	}
	return nil
}

func (c *Client) updateAudioSession(mediaSessionID string, values map[string]any) {
	c.stateMu.Lock()
	session := mapValue(c.state["audioSession"])
	if mediaSessionID != "" {
		session["mediaSessionId"] = mediaSessionID
	}
	for key, value := range values {
		session[key] = value
	}
	broadcastActive := boolValue(session["broadcastActive"], false)
	monitorActive := boolValue(session["monitorActive"], false)
	if broadcastActive || monitorActive {
		session["state"] = "ACTIVE"
	} else {
		session["state"] = "IDLE"
		session["mediaSessionId"] = ""
	}
	c.state["audioSession"] = session
	snapshot, handler := cloneMap(c.state), c.stateHandler
	c.stateMu.Unlock()
	notify(handler, snapshot)
}

func (c *Client) updateState(values map[string]any) {
	c.stateMu.Lock()
	for key, value := range values {
		c.state[key] = value
	}
	snapshot, handler := cloneMap(c.state), c.stateHandler
	c.stateMu.Unlock()
	notify(handler, snapshot)
}

func notify(handler func(map[string]any), snapshot map[string]any) {
	if handler != nil {
		handler(snapshot)
	}
}

func (c *Client) httpClient() *http.Client {
	return &http.Client{Timeout: c.cfg.HTTPTimeout}
}

func (c *Client) httpURL(path string) string {
	return "http://" + net.JoinHostPort(c.cfg.Host, strconv.Itoa(c.cfg.HTTPPort)) + path
}

func extractFileNames(body []byte) []string {
	var decoded any
	if json.Unmarshal(body, &decoded) == nil {
		result := make([]string, 0)
		collectFileNames(decoded, &result)
		return uniqueStrings(result)
	}
	lines := strings.FieldsFunc(string(body), func(r rune) bool {
		return r == '\n' || r == '\r' || r == ','
	})
	return uniqueStrings(lines)
}

func ensureDeviceHTTPSuccess(body []byte) error {
	if len(bytes.TrimSpace(body)) == 0 {
		return nil
	}
	var decoded map[string]any
	if err := json.Unmarshal(body, &decoded); err != nil {
		return nil
	}
	value, exists := decoded["code"]
	if !exists {
		return nil
	}
	switch code := value.(type) {
	case float64:
		if code == 0 {
			return nil
		}
	case string:
		if code == "0" {
			return nil
		}
	}
	return fmt.Errorf("device HTTP request failed: %s", strings.TrimSpace(string(body)))
}

func collectFileNames(value any, result *[]string) {
	switch typed := value.(type) {
	case string:
		if strings.TrimSpace(typed) != "" {
			*result = append(*result, strings.TrimSpace(typed))
		}
	case []any:
		for _, item := range typed {
			collectFileNames(item, result)
		}
	case map[string]any:
		for _, key := range []string{"filename", "fileName", "name"} {
			if fileName, ok := typed[key].(string); ok && fileName != "" {
				*result = append(*result, fileName)
				return
			}
		}
		for _, key := range []string{"files", "data", "list", "result"} {
			if nested, ok := typed[key]; ok {
				collectFileNames(nested, result)
			}
		}
	}
}

func uniqueStrings(values []string) []string {
	seen := make(map[string]bool)
	result := make([]string, 0, len(values))
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value == "" || seen[value] {
			continue
		}
		seen[value] = true
		result = append(result, value)
	}
	return result
}

func validateFileName(fileName string) error {
	if strings.TrimSpace(fileName) == "" {
		return errors.New("fileName is required")
	}
	if filepath.Base(fileName) != fileName || strings.Contains(fileName, "..") {
		return errors.New("fileName must not contain a path")
	}
	return nil
}

func intParam(params map[string]any, key string, fallback int) int {
	return intValue(params[key], fallback)
}

func intValue(value any, fallback int) int {
	switch typed := value.(type) {
	case int:
		return typed
	case int64:
		return int(typed)
	case float64:
		return int(typed)
	case json.Number:
		parsed, err := typed.Int64()
		if err == nil {
			return int(parsed)
		}
	case string:
		parsed, err := strconv.Atoi(typed)
		if err == nil {
			return parsed
		}
	}
	return fallback
}

func strictInt(value any) (int, bool) {
	switch typed := value.(type) {
	case int:
		return typed, true
	case int64:
		return int(typed), true
	case int32:
		return int(typed), true
	case float64:
		if typed != float64(int(typed)) {
			return 0, false
		}
		return int(typed), true
	case float32:
		if typed != float32(int(typed)) {
			return 0, false
		}
		return int(typed), true
	case json.Number:
		parsed, err := typed.Int64()
		return int(parsed), err == nil
	default:
		return 0, false
	}
}

func requiredPercent(params map[string]any, key string) (int, error) {
	value, exists := params[key]
	if !exists {
		return 0, fmt.Errorf("%s is required", key)
	}
	percent, ok := strictInt(value)
	if !ok || percent < 0 || percent > 100 {
		return 0, fmt.Errorf("%s must be an integer between 0 and 100", key)
	}
	return percent, nil
}

func stringParam(params map[string]any, key string) string {
	value, _ := params[key].(string)
	return strings.TrimSpace(value)
}

func boolParam(params map[string]any, key string, fallback bool) bool {
	return boolValue(params[key], fallback)
}

func boolValue(value any, fallback bool) bool {
	switch typed := value.(type) {
	case bool:
		return typed
	case string:
		parsed, err := strconv.ParseBool(typed)
		if err == nil {
			return parsed
		}
	case float64:
		return typed != 0
	case int:
		return typed != 0
	}
	return fallback
}

func strictBool(value any) (bool, bool) {
	valueBool, ok := value.(bool)
	return valueBool, ok
}

func mapValue(value any) map[string]any {
	if typed, ok := value.(map[string]any); ok {
		return cloneMap(typed)
	}
	return make(map[string]any)
}

func cloneMap(source map[string]any) map[string]any {
	result := make(map[string]any, len(source))
	for key, value := range source {
		switch typed := value.(type) {
		case map[string]any:
			result[key] = cloneMap(typed)
		case []string:
			result[key] = append([]string{}, typed...)
		default:
			result[key] = typed
		}
	}
	return result
}

func sleepContext(ctx context.Context, duration time.Duration) bool {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}

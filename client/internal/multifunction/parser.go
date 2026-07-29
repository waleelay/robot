package multifunction

import (
	"bytes"
	"encoding/json"
	"fmt"
	"strconv"
)

type streamEvent struct {
	kind    string
	payload []byte
}

type streamParser struct {
	buffer []byte
}

func (p *streamParser) feed(chunk []byte) []streamEvent {
	p.buffer = append(p.buffer, chunk...)
	events := make([]streamEvent, 0)
	for {
		if len(p.buffer) == 0 {
			return events
		}
		switch {
		case bytes.HasPrefix(p.buffer, []byte("[99]")):
			end := quotedJSONEnd(p.buffer[4:])
			if end < 0 {
				return events
			}
			payload := append([]byte(nil), p.buffer[4:4+end]...)
			p.buffer = p.buffer[4+end:]
			events = append(events, streamEvent{kind: "status", payload: payload})
		case bytes.HasPrefix(p.buffer, []byte("[14]")):
			payload, consumed := volumePayload(p.buffer[4:])
			if consumed == 0 {
				return events
			}
			p.buffer = p.buffer[4+consumed:]
			events = append(events, streamEvent{kind: "volume", payload: payload})
		case bytes.HasPrefix(p.buffer, []byte("[30]")):
			p.buffer = p.buffer[4:]
			events = append(events, streamEvent{kind: "tts_finished"})
		case bytes.HasPrefix(p.buffer, []byte("[39]")):
			p.buffer = p.buffer[4:]
			events = append(events, streamEvent{kind: "audio_finished"})
		case bytes.HasPrefix(p.buffer, []byte("[40]")):
			next := nextMarker(p.buffer, 4)
			if next < 0 {
				if len(p.buffer) > 64*1024 {
					p.buffer = p.buffer[:0]
				}
				return events
			}
			payload := append([]byte(nil), p.buffer[4:next]...)
			p.buffer = p.buffer[next:]
			if len(payload) > 0 {
				events = append(events, streamEvent{kind: "monitor_opus", payload: payload})
			}
		case p.buffer[0] == lightHeader:
			if len(p.buffer) < 4 {
				return events
			}
			payloadLength := int(p.buffer[1])
			frameLength := payloadLength + 4
			if payloadLength > 2 || !isLightMessageID(p.buffer[2]) {
				p.buffer = p.buffer[1:]
				continue
			}
			if len(p.buffer) < frameLength {
				return events
			}
			frame := append([]byte(nil), p.buffer[:frameLength]...)
			if CRC8Maxim(frame[1:len(frame)-1]) != frame[len(frame)-1] {
				p.buffer = p.buffer[1:]
				continue
			}
			p.buffer = p.buffer[frameLength:]
			events = append(events, streamEvent{kind: "light_frame", payload: frame})
		default:
			next := nextMarker(p.buffer, 1)
			if next < 0 {
				if len(p.buffer) > 8 {
					p.buffer = append([]byte(nil), p.buffer[len(p.buffer)-8:]...)
				}
				return events
			}
			p.buffer = p.buffer[next:]
		}
	}
}

func volumePayload(data []byte) ([]byte, int) {
	if len(data) == 0 {
		return nil, 0
	}
	if data[0] == '"' {
		end := bytes.IndexByte(data[1:], '"')
		if end < 0 {
			return nil, 0
		}
		end++
		payload := data[1:end]
		if (len(payload) != 1 && len(payload) != 2) || !isHex(payload) {
			return nil, end + 1
		}
		return append([]byte(nil), payload...), end + 1
	}
	if len(data) < 2 {
		return nil, 0
	}
	return append([]byte(nil), data[:2]...), 2
}

func nextMarker(data []byte, start int) int {
	markers := [][]byte{
		[]byte("[99]"),
		[]byte("[14]"),
		[]byte("[30]"),
		[]byte("[39]"),
		[]byte("[40]"),
	}
	found := -1
	for _, marker := range markers {
		if index := bytes.Index(data[start:], marker); index >= 0 {
			index += start
			if found < 0 || index < found {
				found = index
			}
		}
	}
	if index := nextValidLightFrame(data, start); index >= 0 && (found < 0 || index < found) {
		found = index
	}
	return found
}

func nextValidLightFrame(data []byte, start int) int {
	for search := start; search < len(data); {
		relative := bytes.IndexByte(data[search:], lightHeader)
		if relative < 0 {
			return -1
		}
		index := search + relative
		if len(data)-index >= 4 {
			frameLength := int(data[index+1]) + 4
			if data[index+1] <= 2 &&
				isLightMessageID(data[index+2]) &&
				len(data)-index >= frameLength &&
				CRC8Maxim(data[index+1:index+frameLength-1]) == data[index+frameLength-1] {
				return index
			}
		}
		search = index + 1
	}
	return -1
}

func isLightMessageID(value byte) bool {
	switch value {
	case lightPower, lightBrightness, lightStrobe, lightKeepalive, lightRedBlueMode, lightTilt:
		return true
	default:
		return false
	}
}

func isHex(data []byte) bool {
	for _, value := range data {
		if !((value >= '0' && value <= '9') ||
			(value >= 'a' && value <= 'f') ||
			(value >= 'A' && value <= 'F')) {
			return false
		}
	}
	return true
}

func quotedJSONEnd(data []byte) int {
	open := bytes.IndexByte(data, '{')
	if open < 0 {
		return -1
	}
	depth := 0
	inString := false
	escaped := false
	for index := open; index < len(data); index++ {
		value := data[index]
		if inString {
			if escaped {
				escaped = false
				continue
			}
			if value == '\\' {
				escaped = true
			} else if value == '"' {
				inString = false
			}
			continue
		}
		if value == '"' {
			inString = true
		} else if value == '{' {
			depth++
		} else if value == '}' {
			depth--
			if depth == 0 {
				end := index + 1
				if end < len(data) && data[end] == '"' {
					end++
				}
				return end
			}
		}
	}
	return -1
}

func parseDeviceStatus(payload []byte) (map[string]any, error) {
	payload = bytes.TrimSpace(payload)
	if len(payload) >= 2 && payload[0] == '"' && payload[len(payload)-1] == '"' {
		payload = payload[1 : len(payload)-1]
	}
	var raw map[string]any
	if err := json.Unmarshal(payload, &raw); err != nil {
		return nil, fmt.Errorf("parse [99] status: %w", err)
	}
	status := make(map[string]any)
	if value, ok := number(raw["volume_real"]); ok {
		status["volumePercent"] = clamp(int(value), 0, 100)
	}
	if value, ok := number(raw["volume_limit"]); ok {
		status["volumeLimitPercent"] = clamp(int(value), 0, 100)
	}
	if value, ok := number(raw["temperature"]); ok {
		status["temperatureC"] = value
	}
	return status, nil
}

func number(value any) (float64, bool) {
	switch typed := value.(type) {
	case float64:
		return typed, true
	case int:
		return float64(typed), true
	case json.Number:
		parsed, err := typed.Float64()
		return parsed, err == nil
	case string:
		parsed, err := strconv.ParseFloat(typed, 64)
		return parsed, err == nil
	default:
		return 0, false
	}
}

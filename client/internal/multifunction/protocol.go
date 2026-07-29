package multifunction

import (
	"fmt"
	"math"
)

const (
	lightHeader       byte = 0x8D
	lightPower        byte = 0x01
	lightBrightness   byte = 0x02
	lightStrobe       byte = 0x03
	lightKeepalive    byte = 0x04
	lightRedBlueMode  byte = 0x07
	lightTilt         byte = 0x09
	minSpeakerTiltRaw      = 80
	maxSpeakerTiltRaw      = 220
	minLightTiltRaw        = 100
	maxLightTiltRaw        = 200
)

// CRC8Maxim 计算灯光协议使用的 CRC-8/MAXIM。
func CRC8Maxim(data []byte) byte {
	var crc byte
	for _, value := range data {
		crc ^= value
		for range 8 {
			if crc&0x01 != 0 {
				crc = (crc >> 1) ^ 0x8C
			} else {
				crc >>= 1
			}
		}
	}
	return crc
}

// BuildLightFrame 构造 0x8D + 长度 + 消息 ID + 载荷 + CRC。
func BuildLightFrame(messageID byte, payload ...byte) []byte {
	body := make([]byte, 0, len(payload)+2)
	body = append(body, byte(len(payload)), messageID)
	body = append(body, payload...)
	frame := make([]byte, 0, len(body)+2)
	frame = append(frame, lightHeader)
	frame = append(frame, body...)
	frame = append(frame, CRC8Maxim(body))
	return frame
}

func keepaliveFrame() []byte {
	return BuildLightFrame(lightKeepalive)
}

func percentToBrightnessRaw(percent int) byte {
	return byte(math.Round(float64(clamp(percent, 0, 100)) * 30 / 100))
}

func percentToSpeakerTiltRaw(percent int) byte {
	raw := minSpeakerTiltRaw + int(math.Round(float64(clamp(percent, 0, 100))*float64(maxSpeakerTiltRaw-minSpeakerTiltRaw)/100))
	return byte(raw)
}

func percentToLightTiltRaw(percent int) byte {
	raw := minLightTiltRaw + int(math.Round(float64(clamp(percent, 0, 100))*float64(maxLightTiltRaw-minLightTiltRaw)/100))
	return byte(raw)
}

func volumeCommand(percent int) []byte {
	return []byte(fmt.Sprintf("[14]%02x", clamp(percent, 0, 100)))
}

func ttsCommand(text, voice string, loop bool) []byte {
	prefix := "[31]"
	if loop {
		prefix = "[32]"
	}
	voiceCode := "0"
	if voice == "FEMALE" {
		voiceCode = "1"
	}
	return []byte(prefix + voiceCode + text)
}

func audioFileCommand(fileName string, loop bool) []byte {
	loopFlag := "0"
	if loop {
		loopFlag = "1"
	}
	return []byte("[12]" + loopFlag + fileName)
}

func clamp(value, min, max int) int {
	if value < min {
		return min
	}
	if value > max {
		return max
	}
	return value
}

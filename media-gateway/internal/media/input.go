// Package media 定义与输入协议无关的媒体契约，不依赖源目录、MQTT 或具体协议实现。
package media

import "context"

// StreamInfo 保存本地探测出的编码和像素尺寸，不能从业务命令声明推定。
type StreamInfo struct {
	CodecName string
	Width     int
	Height    int
}

// Input 是协议适配器确认的发布输入；Address 由具体协议解释，可能含凭据，不得输出日志。
type Input struct {
	Protocol string
	Address  string
	Info     StreamInfo
}

// Adapter 定义输入校验和探测能力；监听、协商及资源释放必须由未来的具体协议实现补齐。
type Adapter interface {
	Protocol() string
	ValidateAddress(string) error
	Probe(context.Context, string) (StreamInfo, error)
}

// Failure 提供安全的原因码和中文说明，不承载现场地址或第三方原始敏感输出。
type Failure struct {
	Code string
	Text string
}

func (f *Failure) Error() string { return f.Text }

package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"time"

	"robot-media-client/internal/config"
	"robot-media-client/internal/multifunction"
)

func main() {
	var (
		host        = flag.String("host", "192.168.1.27", "多合一设备 IP")
		controlPort = flag.Int("control-port", 8519, "控制和音频 TCP 端口")
		tiltPort    = flag.Int("tilt-port", 12345, "喊话器俯仰 TCP 端口")
		httpPort    = flag.Int("http-port", 8222, "文件管理 HTTP 端口")
		action      = flag.String("action", "status", "status、list_audio_files 或平台 action")
		paramsJSON  = flag.String("params", "{}", "action 参数 JSON")
		uploadPath  = flag.String("upload", "", "上传音频文件路径；设置后忽略 action")
		timeout     = flag.Duration("timeout", 8*time.Second, "单次测试超时")
		execute     = flag.Bool("execute", false, "允许执行会改变设备状态的动作")
	)
	flag.Parse()

	if *action != "status" && *action != "list_audio_files" && *uploadPath == "" && !*execute {
		log.Fatal("该动作会改变真实设备状态，确认现场安全后增加 -execute")
	}
	if *uploadPath != "" && !*execute {
		log.Fatal("上传会改变真实设备文件，确认后增加 -execute")
	}

	var params map[string]any
	if err := json.Unmarshal([]byte(*paramsJSON), &params); err != nil {
		log.Fatalf("解析 -params 失败: %v", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), *timeout)
	defer cancel()
	client := multifunction.New(config.MultiFunctionConfig{
		Enabled:           true,
		DeviceID:          "broadcaster-001",
		Host:              *host,
		ControlPort:       *controlPort,
		TiltPort:          *tiltPort,
		HTTPPort:          *httpPort,
		DialTimeout:       3 * time.Second,
		WriteTimeout:      3 * time.Second,
		HTTPTimeout:       5 * time.Second,
		KeepaliveEnabled:  true,
		KeepaliveInterval: 2 * time.Second,
	})
	client.SetStateHandler(func(state map[string]any) {
		body, _ := json.Marshal(state)
		log.Printf("设备状态: %s", body)
	})
	client.Start(ctx)
	defer client.Close()

	var (
		state map[string]any
		err   error
	)
	switch {
	case *uploadPath != "":
		err = client.UploadAudioFile(ctx, *uploadPath)
		state = client.Snapshot()
	case *action == "status":
		state, err = waitForStatus(ctx, client)
	default:
		state, err = client.Execute(ctx, *action, params)
	}
	if err != nil {
		log.Fatalf("测试失败: %v", err)
	}
	body, err := json.MarshalIndent(state, "", "  ")
	if err != nil {
		log.Fatal(err)
	}
	fmt.Fprintln(os.Stdout, string(body))
}

func waitForStatus(ctx context.Context, client *multifunction.Client) (map[string]any, error) {
	ticker := time.NewTicker(50 * time.Millisecond)
	defer ticker.Stop()
	for {
		state := client.Snapshot()
		connected, _ := state["connected"].(bool)
		_, hasVolume := state["volumePercent"]
		if connected && hasVolume {
			return state, nil
		}
		select {
		case <-ctx.Done():
			return state, fmt.Errorf("等待设备状态超时: %w", ctx.Err())
		case <-ticker.C:
		}
	}
}

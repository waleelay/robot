package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"robot-media-client/internal/config"
	"robot-media-client/internal/intercom"
	mq "robot-media-client/internal/mqtt"
	"robot-media-client/internal/publisher"
	"robot-media-client/internal/recordingupload"
	"robot-media-client/internal/rtsp"
)

func main() {
	cfg := config.Load()
	// 统一用一个根 context 接收 SIGINT/SIGTERM。MQTT、推流进程、对讲音频桥、
	// 录像上传 runner 都挂在这个 context 下，进程退出时可以一起收尾。
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// probe 先检查 RTSP 是否可达；publisher 负责把 RTSP 推到 LiveKit；
	// audio 负责对讲时的本地麦克风/扬声器与 LiveKit 音频 track 桥接。
	probe := rtsp.NewProbe(cfg.FFprobePath, cfg.ProbeTimeout)
	pub := publisher.NewProcessPublisher(cfg)
	audio := intercom.NewSDKManager(cfg)
	log.Printf("client starting robotId=%s recordingUploadEnabled=%t recordingDirectory=%s mediaService=%s",
		cfg.RobotID,
		cfg.RecordingUploadEnabled,
		cfg.RecordingDirectory,
		cfg.MediaServiceURL)
	if cfg.RecordingUploadEnabled {
		// 录像上传是后台轮询任务，独立于 MQTT 控制链路。即使没有实时视频命令，
		// 也会持续扫描本地目录并把 mp4 续传到媒体服务。
		runner := recordingupload.NewRunner(cfg)
		go runner.Run(ctx)
	} else {
		log.Println("recording upload disabled")
	}
	client := mq.NewClient(cfg, probe, pub, audio)
	for ctx.Err() == nil {
		// MQTT 连接断开时 Run 会返回错误；这里做简单退避重连。
		// context 取消后不再重连，Run 内部会停止推流/对讲并上报 offline。
		if err := client.Run(ctx); err != nil {
			log.Println("mqtt client stopped", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(5 * time.Second):
			}
		}
	}
}

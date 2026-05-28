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
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	probe := rtsp.NewProbe(cfg.FFprobePath, cfg.ProbeTimeout)
	pub := publisher.NewProcessPublisher(cfg)
	audio := intercom.NewSDKManager(cfg)
	log.Printf("client starting robotId=%s recordingUploadEnabled=%t recordingDirectory=%s mediaService=%s",
		cfg.RobotID,
		cfg.RecordingUploadEnabled,
		cfg.RecordingDirectory,
		cfg.MediaServiceURL)
	if cfg.RecordingUploadEnabled {
		runner := recordingupload.NewRunner(cfg)
		go runner.Run(ctx)
	} else {
		log.Println("recording upload disabled")
	}
	client := mq.NewClient(cfg, probe, pub, audio)
	for ctx.Err() == nil {
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

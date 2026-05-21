package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"

	"robot-media-client/internal/config"
	mq "robot-media-client/internal/mqtt"
	"robot-media-client/internal/publisher"
	"robot-media-client/internal/rtsp"
)

func main() {
	cfg := config.Load()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	probe := rtsp.NewProbe(cfg.FFprobePath, cfg.ProbeTimeout)
	pub := publisher.NewProcessPublisher(cfg)
	client := mq.NewClient(cfg, probe, pub)
	if err := client.Run(ctx); err != nil {
		log.Fatal(err)
	}
}

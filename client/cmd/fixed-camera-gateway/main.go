package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"robot-media-client/internal/config"
	"robot-media-client/internal/fixedcamera"
	"robot-media-client/internal/publisher"
	"robot-media-client/internal/rtsp"
)

func main() {
	cfg := config.Load()
	if cfg.ClientID == "robot-media-client" {
		cfg.ClientID = "fixed-camera-gateway-" + cfg.FixedCameraGatewayID
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	probe := rtsp.NewProbe(cfg.FFprobePath, cfg.ProbeTimeout)
	pub := publisher.NewProcessPublisher(cfg)
	gateway := fixedcamera.NewGateway(cfg, probe, pub)
	for ctx.Err() == nil {
		if err := gateway.Run(ctx); err != nil {
			log.Println("fixed camera gateway stopped", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(5 * time.Second):
			}
		}
	}
}

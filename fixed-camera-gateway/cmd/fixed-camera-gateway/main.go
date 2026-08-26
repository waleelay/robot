package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"fixed-camera-gateway/internal/config"
	"fixed-camera-gateway/internal/fixedcamera"
	"fixed-camera-gateway/internal/publisher"
	"fixed-camera-gateway/internal/rtsp"
)

func main() {
	cfg := config.Load()
	if cfg.ClientID == "fixed-camera-gateway" {
		cfg.ClientID = "fixed-camera-gateway-" + cfg.GatewayID
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	probe := rtsp.NewProbe(cfg.FFprobePath, cfg.ProbeTimeout)
	pub := publisher.NewProcessPublisher(cfg)
	startObservabilityServer(ctx, cfg.HTTPAddr, pub)
	gateway := fixedcamera.NewGateway(cfg, probe, pub)
	go gateway.ObservePublisherEvents(ctx, pub.Events())
	for ctx.Err() == nil {
		if err := gateway.Run(ctx); err != nil {
			log.Printf("固定摄像头网关运行中断：%v", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(5 * time.Second):
			}
		}
	}
}

func startObservabilityServer(ctx context.Context, address string, pub *publisher.ProcessPublisher) {
	if address == "" {
		return
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(writer http.ResponseWriter, _ *http.Request) {
		writer.Header().Set("Content-Type", "application/json; charset=utf-8")
		_ = json.NewEncoder(writer).Encode(map[string]any{
			"status":    "UP",
			"publisher": pub.Snapshot(),
		})
	})
	mux.HandleFunc("/metrics", func(writer http.ResponseWriter, _ *http.Request) {
		stats := pub.Snapshot()
		writer.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
		_, _ = fmt.Fprintf(writer,
			"fixed_camera_publisher_active %d\n"+
				"fixed_camera_publisher_sessions_active %d\n"+
				"fixed_camera_publisher_unexpected_exits_total %d\n"+
				"fixed_camera_publisher_token_expirations_total %d\n"+
				"fixed_camera_publisher_forced_kills_total %d\n"+
				"fixed_camera_publisher_cleanup_last_milliseconds %d\n"+
				"fixed_camera_publisher_cleanup_max_milliseconds %d\n",
			stats.ActivePublishers, stats.ActiveSessions, stats.UnexpectedExits,
			stats.TokenExpirations, stats.ForcedKills, stats.LastCleanupMillis, stats.MaxCleanupMillis)
	})
	server := &http.Server{Addr: address, Handler: mux, ReadHeaderTimeout: 3 * time.Second}
	go func() {
		<-ctx.Done()
		shutdownContext, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := server.Shutdown(shutdownContext); err != nil {
			log.Printf("固定摄像头观测服务关闭失败：%v", err)
		}
	}()
	go func() {
		log.Printf("固定摄像头观测服务已启动，监听地址=%s", address)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("固定摄像头观测服务运行失败：%v", err)
		}
	}()
}

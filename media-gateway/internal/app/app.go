// Package app 为统一媒体入口提供同一运行时。
package app

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

	"media-gateway/internal/config"
	"media-gateway/internal/gateway"
	"media-gateway/internal/protocol/rtsp"
	"media-gateway/internal/publisher"
	"media-gateway/internal/source"
)

// Run 初始化受控源、MQTT、发布进程与观测接口，并在信号到达时收口资源。
func Run(cfg config.Config) error {
	if err := cfg.ValidatePublisherTemplates(); err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	probe := rtsp.NewProbe(cfg.FFprobePath, cfg.ProbeTimeout)
	pub := publisher.NewProcessPublisher(cfg)
	var configs []source.Config
	var err error
	if cfg.SourcesPath != "" {
		configs, err = source.LoadConfigs(cfg.SourcesPath)
		if err != nil {
			return err
		}
	}
	registry, err := source.NewRegistry(configs, rtsp.NewAdapter(probe))
	if err != nil {
		return err
	}
	gw, err := gateway.NewMediaGateway(cfg, registry, pub)
	if err != nil {
		return err
	}
	startObservabilityServer(ctx, cfg.HTTPAddr, pub, gw.Ready)
	go gw.ObservePublisherEvents(ctx, pub.Events())
	for ctx.Err() == nil {
		if err := gw.Run(ctx); err != nil {
			log.Printf("媒体网关运行中断：%v", err)
			select {
			case <-ctx.Done():
				return nil
			case <-time.After(5 * time.Second):
			}
		}
	}
	return nil
}

func observabilityHandler(pub *publisher.ProcessPublisher, ready func() bool) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(writer http.ResponseWriter, _ *http.Request) {
		writer.Header().Set("Content-Type", "application/json; charset=utf-8")
		status := "UP"
		if !ready() {
			status = "DOWN"
			writer.WriteHeader(http.StatusServiceUnavailable)
		}
		_ = json.NewEncoder(writer).Encode(map[string]any{
			"status":    status,
			"publisher": pub.Snapshot(),
		})
	})
	mux.HandleFunc("/metrics", func(writer http.ResponseWriter, _ *http.Request) {
		stats := pub.Snapshot()
		prefix := "media_gateway"
		writer.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
		_, _ = fmt.Fprintf(writer,
			"%s_publisher_active %d\n"+
				"%s_publisher_sessions_active %d\n"+
				"%s_publisher_unexpected_exits_total %d\n"+
				"%s_publisher_forced_kills_total %d\n"+
				"%s_publisher_cleanup_last_milliseconds %d\n"+
				"%s_publisher_cleanup_max_milliseconds %d\n",
			prefix, stats.ActivePublishers, prefix, stats.ActiveSessions, prefix, stats.UnexpectedExits,
			prefix, stats.ForcedKills, prefix, stats.LastCleanupMillis, prefix, stats.MaxCleanupMillis)
	})
	return mux
}

func startObservabilityServer(ctx context.Context, address string, pub *publisher.ProcessPublisher, ready func() bool) {
	if address == "" {
		return
	}
	server := &http.Server{Addr: address, Handler: observabilityHandler(pub, ready), ReadHeaderTimeout: 3 * time.Second}
	go func() {
		<-ctx.Done()
		shutdownContext, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := server.Shutdown(shutdownContext); err != nil {
			log.Printf("媒体观测服务关闭失败：%v", err)
		}
	}()
	go func() {
		log.Printf("媒体观测服务已启动，监听地址=%s", address)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("媒体观测服务运行失败：%v", err)
		}
	}()
}

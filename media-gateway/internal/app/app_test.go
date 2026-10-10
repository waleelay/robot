package app

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"media-gateway/internal/config"
	"media-gateway/internal/publisher"
)

func TestRunRejectsLegacyTemplateBeforeConnecting(t *testing.T) {
	err := Run(config.Config{PublisherCmd: "publisher {rtsp}"})
	if err == nil || !strings.Contains(err.Error(), "{input}") {
		t.Fatalf("旧模板应先于网络连接和进程创建被拒绝：%v", err)
	}
}

func TestHealthRequiresMQTTAndSubscriptionsReady(t *testing.T) {
	pub := publisher.NewProcessPublisher(config.Config{})
	ready := false
	handler := observabilityHandler(pub, func() bool { return ready })
	for _, test := range []struct {
		ready  bool
		code   int
		status string
	}{
		{false, http.StatusServiceUnavailable, "DOWN"}, {true, http.StatusOK, "UP"},
	} {
		ready = test.ready
		response := httptest.NewRecorder()
		handler.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/health", nil))
		var body struct {
			Status string `json:"status"`
		}
		if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
			t.Fatal(err)
		}
		if response.Code != test.code || body.Status != test.status {
			t.Fatalf("健康不能只代表进程存活：%d %+v", response.Code, body)
		}
	}
}

func TestMetricsKeepEntryPointNamespace(t *testing.T) {
	pub := publisher.NewProcessPublisher(config.Config{})
	response := httptest.NewRecorder()
	observabilityHandler(pub, func() bool { return true }).ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/metrics", nil))
	if !strings.Contains(response.Body.String(), "media_gateway_publisher_active 0") {
		t.Fatalf("观测命名空间错误：%s", response.Body.String())
	}
}

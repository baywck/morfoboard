package health

import (
	"encoding/json"
	"net/http"
	"time"

	"morfoboard-backend/internal/config"
)

type HealthResponse struct {
	Status    string `json:"status"`
	Version   string `json:"version"`
	AIBackend string `json:"ai_backend"`
}

func Handler(cfg *config.Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		aiStatus := "not_configured"

		if cfg.NineRouterURL != "" {
			aiStatus = checkNineRouter(cfg.NineRouterURL)
		}

		resp := HealthResponse{
			Status:    "ok",
			Version:   "0.1.0",
			AIBackend: aiStatus,
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
	}
}

func checkNineRouter(url string) string {
	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get(url + "/v1/models")
	if err != nil {
		return "disconnected"
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusOK {
		return "connected"
	}
	return "disconnected"
}

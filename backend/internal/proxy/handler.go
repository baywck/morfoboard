package proxy

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"morfoboard-backend/internal/auth"
	"morfoboard-backend/internal/config"
)

type ProcessRequest struct {
	Action         string `json:"action"`
	Text           string `json:"text"`
	SourceLanguage string `json:"source_language"`
	TargetLanguage string `json:"target_language"`
	Tone           string `json:"tone"`
}

type ProcessResponse struct {
	Success    bool   `json:"success"`
	Original   string `json:"original,omitempty"`
	Result     string `json:"result,omitempty"`
	Action     string `json:"action,omitempty"`
	Error      string `json:"error,omitempty"`
	Message    string `json:"message,omitempty"`
	ModelUsed  string `json:"model_used,omitempty"`
	DurationMs int64  `json:"processing_time_ms,omitempty"`
}

type Handler struct {
	cfg      *config.Config
	verifier *auth.Verifier
	client   *NineRouterClient
}

func NewHandler(cfg *config.Config) *Handler {
	return &Handler{
		cfg:      cfg,
		verifier: auth.NewVerifier(cfg.GoogleClientID),
		client:   NewNineRouterClient(cfg),
	}
}

func (h *Handler) Process(w http.ResponseWriter, r *http.Request) {
	// Verify auth token
	authHeader := r.Header.Get("Authorization")
	if authHeader == "" || !strings.HasPrefix(authHeader, "Bearer ") {
		writeError(w, http.StatusUnauthorized, "unauthorized", "Missing or invalid Authorization header")
		return
	}

	token := strings.TrimPrefix(authHeader, "Bearer ")
	userInfo, err := h.verifier.Verify(r.Context(), token)
	if err != nil {
		slog.Warn("auth verification failed", "error", err)
		writeError(w, http.StatusUnauthorized, "unauthorized", "Invalid or expired token")
		return
	}

	// Log user info
	slog.Info("authenticated request", "user", userInfo.Email)

	// Parse request
	var req ProcessRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_request", "Invalid JSON body")
		return
	}

	// Special action for testing sign-in
	if req.Action == "login_test" {
		resp := ProcessResponse{
			Success: true,
			Result:  "Authenticated as " + userInfo.Name + " (" + userInfo.Email + ")! Random string: " + generateRandomString(12),
			Action:  req.Action,
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
		return
	}

	// Validate
	if req.Text == "" {
		writeError(w, http.StatusBadRequest, "invalid_request", "text is required")
		return
	}
	if len(req.Text) > 5000 {
		writeError(w, http.StatusBadRequest, "invalid_request", "text must be 5000 characters or less")
		return
	}
	if req.Action != "translate" && req.Action != "fix_text" {
		writeError(w, http.StatusBadRequest, "invalid_request", "action must be 'translate' or 'fix_text'")
		return
	}

	// Check AI availability
	if !h.client.IsAvailable() {
		writeError(w, http.StatusBadGateway, "ai_unavailable", "AI service is currently unavailable")
		return
	}

	// Build prompt based on action
	var systemPrompt string
	switch req.Action {
	case "translate":
		if req.TargetLanguage == "" {
			req.TargetLanguage = "en"
		}
		if req.SourceLanguage == "" {
			req.SourceLanguage = "auto"
		}
		if req.Tone == "" {
			req.Tone = "natural"
		}
		systemPrompt = TranslationPrompt(req.SourceLanguage, req.TargetLanguage, req.Tone)
	case "fix_text":
		systemPrompt = FixTextPrompt()
	}

	// Call AI
	result, err := h.client.ChatCompletion(systemPrompt, req.Text)
	if err != nil {
		slog.Error("AI call failed", "error", err, "action", req.Action)
		if strings.Contains(err.Error(), "timeout") || strings.Contains(err.Error(), "deadline") {
			writeError(w, http.StatusGatewayTimeout, "timeout", "AI processing timed out. Please try again.")
			return
		}
		writeError(w, http.StatusBadGateway, "ai_error", "AI service error. Please try again.")
		return
	}

	// Return result
	resp := ProcessResponse{
		Success:    true,
		Original:   req.Text,
		Result:     result.Text,
		Action:     req.Action,
		ModelUsed:  result.Model,
		DurationMs: result.DurationMs,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

func writeError(w http.ResponseWriter, status int, errCode string, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(ProcessResponse{
		Success: false,
		Error:   errCode,
		Message: message,
	})
}

func generateRandomString(n int) string {
	const letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	b := make([]byte, n)
	for i := range b {
		b[i] = letters[time.Now().UnixNano()%int64(len(letters))]
		time.Sleep(1 * time.Nanosecond) // Ensure different nano time
	}
	return string(b)
}

package users

import (
	"encoding/json"
	"net/http"
)

// AdminHandler provides HTTP endpoints for user management.
type AdminHandler struct {
	store    *Store
	adminKey string
}

// NewAdminHandler creates a new admin handler.
func NewAdminHandler(store *Store, adminKey string) *AdminHandler {
	return &AdminHandler{store: store, adminKey: adminKey}
}

// ListUsers returns all users with their usage stats.
// GET /api/v1/admin/users?key=<admin_key>
func (h *AdminHandler) ListUsers(w http.ResponseWriter, r *http.Request) {
	if !h.checkAuth(w, r) {
		return
	}

	users := h.store.ListUsers()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"total": len(users),
		"users": users,
	})
}

// Blacklist adds or removes a user from the blacklist.
// POST /api/v1/admin/blacklist?key=<admin_key>
// Body: {"email": "user@example.com", "blacklisted": true}
func (h *AdminHandler) Blacklist(w http.ResponseWriter, r *http.Request) {
	if !h.checkAuth(w, r) {
		return
	}

	var req struct {
		Email       string `json:"email"`
		Blacklisted bool   `json:"blacklisted"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid json"}`, http.StatusBadRequest)
		return
	}

	if req.Email == "" {
		http.Error(w, `{"error":"email is required"}`, http.StatusBadRequest)
		return
	}

	ok := h.store.SetBlacklist(req.Email, req.Blacklisted)
	if !ok {
		http.Error(w, `{"error":"user not found"}`, http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"success": true,
		"email":   req.Email,
		"blacklisted": req.Blacklisted,
	})
}

func (h *AdminHandler) checkAuth(w http.ResponseWriter, r *http.Request) bool {
	key := r.URL.Query().Get("key")
	if key == "" || key != h.adminKey {
		http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
		return false
	}
	return true
}

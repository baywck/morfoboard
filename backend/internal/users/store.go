package users

import (
	"encoding/json"
	"os"
	"sync"
	"time"
)

// User represents a registered user with usage tracking.
type User struct {
	Email       string    `json:"email"`
	Name        string    `json:"name"`
	FirstSeen   time.Time `json:"first_seen"`
	LastSeen    time.Time `json:"last_seen"`
	RequestCount int64    `json:"request_count"`
	Blacklisted bool      `json:"blacklisted"`
}

// Store is a simple JSON-file-backed user store.
type Store struct {
	mu       sync.RWMutex
	users    map[string]*User // keyed by email
	filePath string
}

// NewStore creates or loads a user store from the given file path.
func NewStore(filePath string) *Store {
	s := &Store{
		users:    make(map[string]*User),
		filePath: filePath,
	}
	s.load()
	return s
}

// RecordRequest logs a user request. Returns false if user is blacklisted.
func (s *Store) RecordRequest(email, name string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now()

	user, exists := s.users[email]
	if !exists {
		user = &User{
			Email:     email,
			Name:      name,
			FirstSeen: now,
		}
		s.users[email] = user
	}

	if user.Blacklisted {
		return false
	}

	user.LastSeen = now
	user.RequestCount++
	if name != "" {
		user.Name = name
	}

	s.persist()
	return true
}

// IsBlacklisted checks if a user is blacklisted.
func (s *Store) IsBlacklisted(email string) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()

	user, exists := s.users[email]
	if !exists {
		return false
	}
	return user.Blacklisted
}

// SetBlacklist sets the blacklist status for a user.
func (s *Store) SetBlacklist(email string, blacklisted bool) bool {
	s.mu.Lock()
	defer s.mu.Unlock()

	user, exists := s.users[email]
	if !exists {
		return false
	}
	user.Blacklisted = blacklisted
	s.persist()
	return true
}

// ListUsers returns all users.
func (s *Store) ListUsers() []*User {
	s.mu.RLock()
	defer s.mu.RUnlock()

	result := make([]*User, 0, len(s.users))
	for _, u := range s.users {
		result = append(result, u)
	}
	return result
}

// GetUser returns a single user by email.
func (s *Store) GetUser(email string) *User {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.users[email]
}

func (s *Store) load() {
	data, err := os.ReadFile(s.filePath)
	if err != nil {
		return // File doesn't exist yet, start fresh
	}

	var users map[string]*User
	if err := json.Unmarshal(data, &users); err != nil {
		return
	}
	s.users = users
}

func (s *Store) persist() {
	data, err := json.MarshalIndent(s.users, "", "  ")
	if err != nil {
		return
	}
	os.WriteFile(s.filePath, data, 0644)
}

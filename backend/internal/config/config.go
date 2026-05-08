package config

import (
	"fmt"
	"os"
)

type Config struct {
	Port             string
	NineRouterURL    string
	NineRouterAPIKey string
	GoogleClientID   string
}

func LoadFromEnv() *Config {
	return &Config{
		Port:             getEnv("PORT", "8080"),
		NineRouterURL:    os.Getenv("NINE_ROUTER_URL"),
		NineRouterAPIKey: os.Getenv("NINE_ROUTER_API_KEY"),
		GoogleClientID:   os.Getenv("GOOGLE_CLIENT_ID"),
	}
}

func (c *Config) Validate() error {
	missing := []string{}
	if c.NineRouterURL == "" {
		missing = append(missing, "NINE_ROUTER_URL")
	}
	if c.NineRouterAPIKey == "" {
		missing = append(missing, "NINE_ROUTER_API_KEY")
	}
	if c.GoogleClientID == "" {
		missing = append(missing, "GOOGLE_CLIENT_ID")
	}
	if len(missing) > 0 {
		return fmt.Errorf("missing required env vars: %v (AI features will be unavailable)", missing)
	}
	return nil
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}
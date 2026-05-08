package proxy

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"morfoboard-backend/internal/config"
)

type NineRouterClient struct {
	baseURL string
	apiKey  string
	client  *http.Client
}

type chatRequest struct {
	Model    string        `json:"model"`
	Messages []chatMessage `json:"messages"`
	Stream   bool          `json:"stream"`
}

type chatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type chatResponse struct {
	Choices []struct {
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
	Model string `json:"model"`
}

type AIResult struct {
	Text        string
	Model       string
	DurationMs  int64
}

func NewNineRouterClient(cfg *config.Config) *NineRouterClient {
	return &NineRouterClient{
		baseURL: strings.TrimRight(cfg.NineRouterURL, "/"),
		apiKey:  cfg.NineRouterAPIKey,
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

func (c *NineRouterClient) IsAvailable() bool {
	req, err := http.NewRequest("GET", c.baseURL+"/v1/models", nil)
	if err != nil {
		return false
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)

	resp, err := c.client.Do(req)
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	return resp.StatusCode == http.StatusOK
}

func (c *NineRouterClient) ChatCompletion(systemPrompt, userMessage string) (*AIResult, error) {
	start := time.Now()

	body := chatRequest{
		Model: "MORFOBOARD",
		Messages: []chatMessage{
			{Role: "system", Content: systemPrompt},
			{Role: "user", Content: userMessage},
		},
		Stream: false,
	}

	jsonBody, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("marshal request: %w", err)
	}

	req, err := http.NewRequest("POST", c.baseURL+"/v1/chat/completions", bytes.NewReader(jsonBody))
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+c.apiKey)

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("9router request: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response body: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("9router returned %d: %s", resp.StatusCode, string(respBody))
	}

	var chatResp chatResponse
	rawBody := string(respBody)

	// Handle SSE format if the server forced it (response starts with "data: ")
	if strings.HasPrefix(rawBody, "data: ") {
		// Just extract the first JSON object from the stream
		lines := strings.Split(rawBody, "\n")
		for _, line := range lines {
			if strings.HasPrefix(line, "data: ") {
				data := strings.TrimPrefix(line, "data: ")
				if data == "[DONE]" || data == "" {
					continue
				}
				if err := json.Unmarshal([]byte(data), &chatResp); err == nil {
					// Successfully parsed a chunk
					break
				}
			}
		}
	} else {
		if err := json.Unmarshal(respBody, &chatResp); err != nil {
			return nil, fmt.Errorf("decode response: %w (body: %s)", err, rawBody)
		}
	}

	if len(chatResp.Choices) == 0 {
		return nil, fmt.Errorf("no choices in response (body: %s)", rawBody)
	}

	duration := time.Since(start).Milliseconds()

	return &AIResult{
		Text:       strings.TrimSpace(chatResp.Choices[0].Message.Content),
		Model:      chatResp.Model,
		DurationMs: duration,
	}, nil
}

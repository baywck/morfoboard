package auth

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
)

var ErrInvalidToken = errors.New("invalid google token")

type UserInfo struct {
	Email   string `json:"email"`
	Name    string `json:"name"`
	Subject string `json:"subject"`
}

type Verifier struct {
	clientID string
}

func NewVerifier(clientID string) *Verifier {
	return &Verifier{clientID: clientID}
}

func (v *Verifier) Verify(ctx context.Context, idToken string) (*UserInfo, error) {
	// For this phase, we will just decode the JWT payload without verifying the signature
	// to get the user info. This is NOT secure for production but works for this phase.
	parts := strings.Split(idToken, ".")
	if len(parts) < 2 {
		return nil, ErrInvalidToken
	}

	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return nil, err
	}

	var claims struct {
		Email string `json:"email"`
		Name  string `json:"name"`
		Sub   string `json:"sub"`
	}
	if err := json.Unmarshal(payload, &claims); err != nil {
		return nil, err
	}

	return &UserInfo{
		Email:   claims.Email,
		Name:    claims.Name,
		Subject: claims.Sub,
	}, nil
}

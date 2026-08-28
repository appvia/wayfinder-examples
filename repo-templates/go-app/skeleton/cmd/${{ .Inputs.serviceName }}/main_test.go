package main

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestProbesReportOK(t *testing.T) {
	router := newRouter(slog.New(slog.NewTextHandler(io.Discard, nil)))

	for _, path := range []string{"/healthz", "/readyz"} {
		t.Run(path, func(t *testing.T) {
			recorder := httptest.NewRecorder()
			router.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, path, nil))

			if recorder.Code != http.StatusOK {
				t.Fatalf("got status %d, want %d", recorder.Code, http.StatusOK)
			}
		})
	}
}

func TestRootReportsTheServiceIdentity(t *testing.T) {
	router := newRouter(slog.New(slog.NewTextHandler(io.Discard, nil)))

	recorder := httptest.NewRecorder()
	router.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/", nil))

	if recorder.Code != http.StatusOK {
		t.Fatalf("got status %d, want %d", recorder.Code, http.StatusOK)
	}

	var body map[string]string
	if err := json.NewDecoder(recorder.Body).Decode(&body); err != nil {
		t.Fatalf("decoding response: %v", err)
	}

	if got, want := body["service"], "${{ .Inputs.serviceName }}"; got != want {
		t.Errorf("service = %q, want %q", got, want)
	}
	if body["release"] == "" {
		t.Error("release is empty; it should always report something, even 'dev'")
	}
}

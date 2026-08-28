// Command ${{ .Inputs.serviceName }} is ${{ .Inputs.description }}
//
// It listens on PORT and serves three endpoints:
//
//	GET /         the service identity and release
//	GET /healthz  liveness  — is the process alive
//	GET /readyz   readiness — is the process ready to take traffic
//
// The liveness and readiness endpoints are what the Helm chart in charts/app
// wires up as probes, so keep them cheap and dependency-free.
package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

// release is set at build time with -ldflags "-X main.release=...". The CI
// workflows pass the git tag for a release build and the short SHA otherwise,
// so a running pod can always be traced back to a commit.
var release = "dev"

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	port := os.Getenv("PORT")
	if port == "" {
		port = "${{ .Inputs.port }}"
	}

	server := &http.Server{
		Addr:              ":" + port,
		Handler:           newRouter(logger),
		ReadHeaderTimeout: 10 * time.Second,
	}

	// Shut down on SIGTERM so Kubernetes rolling updates drain in-flight
	// requests instead of severing them.
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	go func() {
		logger.Info("listening", "port", port, "release", release)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("server stopped", "error", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	logger.Info("shutting down")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Error("shutdown failed", "error", err)
		os.Exit(1)
	}
}

func newRouter(logger *slog.Logger) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		fmt.Fprintln(w, "ok")
	})

	mux.HandleFunc("GET /readyz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		fmt.Fprintln(w, "ok")
	})

	mux.HandleFunc("GET /", func(w http.ResponseWriter, _ *http.Request) {
		// BUCKET_NAME is set by the Helm chart from the storage component's
		// output. The workload reaches the bucket through its own cloud
		// identity, so there are no credentials to read here.
		body := map[string]string{
			"service": "${{ .Inputs.serviceName }}",
			"release": release,
			"bucket":  os.Getenv("BUCKET_NAME"),
		}
		w.Header().Set("Content-Type", "application/json")
		if err := json.NewEncoder(w).Encode(body); err != nil {
			logger.Error("writing response", "error", err)
		}
	})

	return mux
}

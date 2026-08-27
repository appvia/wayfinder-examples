// Command ${{ .Inputs.serviceName }} serves ${{ .Inputs.summary | default "HTTP requests" }}.
package main

import (
	"fmt"
	"log/slog"
	"net/http"
	"os"
)

const defaultPort = "${{ .Inputs.port }}"

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = defaultPort
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		fmt.Fprintln(w, "ok")
	})
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		slog.Info("request", "method", r.Method, "path", r.URL.Path)
		fmt.Fprintln(w, "${{ .Inputs.serviceName }}")
	})

	slog.Info("listening", "port", port)
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		slog.Error("server stopped", "error", err)
		os.Exit(1)
	}
}

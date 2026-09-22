// Package api serves the order API behind API Gateway. CloudFront forwards
// /api/* here verbatim, so the paths below are the paths the browser asks for.
package api

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/aws/aws-lambda-go/events"
	"github.com/google/uuid"

	"github.com/${{ .Repo.Organization }}/${{ .Repo.Name }}/internal/orders"
)

// defaultListLimit and maxListLimit bound ?limit= on GET /api/orders.
const (
	defaultListLimit = 50
	maxListLimit     = 200
)

// Handler answers API Gateway v2 HTTP requests.
type Handler struct {
	Store   orders.Store
	Release string
	Log     *slog.Logger
}

// Handle routes one request. An unknown path is a 404 rather than a fallthrough
// to the SPA, so a mistyped API call does not come back as HTML the browser
// then fails to parse as JSON.
func (h *Handler) Handle(ctx context.Context, req events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
	switch req.RawPath {
	case "/api/healthz":
		return h.json(http.StatusOK, map[string]string{"status": "ok", "release": h.Release})
	case "/api/orders":
		switch req.RequestContext.HTTP.Method {
		case http.MethodPost:
			return h.placeOrder(ctx, req)
		case http.MethodGet:
			return h.listOrders(ctx, req)
		}
		return h.json(http.StatusMethodNotAllowed, errorBody(req.RequestContext.HTTP.Method+" is not allowed on /api/orders"))
	}
	return h.json(http.StatusNotFound, errorBody("no such endpoint: "+req.RawPath))
}

func (h *Handler) placeOrder(ctx context.Context, req events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
	var o orders.Order
	if err := json.Unmarshal([]byte(req.Body), &o); err != nil {
		return h.json(http.StatusBadRequest, errorBody("the request body is not valid JSON: "+err.Error()))
	}
	if err := o.Validate(); err != nil {
		return h.json(http.StatusBadRequest, errorBody(err.Error()))
	}

	// The id, the total and the timestamp are ours to set: a client that sends
	// its own gets them replaced.
	o.OrderID = uuid.NewString()
	o.ReceivedAt = time.Now().UTC()
	o.Totalise()

	if err := h.Store.Put(ctx, &o); err != nil {
		h.Log.Error("writing the order", "order_id", o.OrderID, "error", err)
		return h.json(http.StatusInternalServerError, errorBody("the order could not be saved"))
	}
	return h.json(http.StatusCreated, &o)
}

func (h *Handler) listOrders(ctx context.Context, req events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
	limit := defaultListLimit
	if raw := req.QueryStringParameters["limit"]; raw != "" {
		n, err := strconv.Atoi(raw)
		if err != nil || n < 1 {
			return h.json(http.StatusBadRequest, errorBody("limit must be a positive whole number, got "+strconv.Quote(raw)))
		}
		limit = min(n, maxListLimit)
	}

	found, err := h.Store.List(ctx, limit)
	if err != nil {
		h.Log.Error("listing orders", "error", err)
		return h.json(http.StatusInternalServerError, errorBody("the orders could not be read"))
	}
	if found == nil {
		found = []*orders.Order{}
	}
	return h.json(http.StatusOK, map[string]any{"orders": found})
}

func errorBody(message string) map[string]string {
	return map[string]string{"error": message}
}

func (h *Handler) json(status int, body any) (events.APIGatewayV2HTTPResponse, error) {
	encoded, err := json.Marshal(body)
	if err != nil {
		h.Log.Error("encoding the response", "error", err)
		return events.APIGatewayV2HTTPResponse{
			StatusCode: http.StatusInternalServerError,
			Headers:    map[string]string{"Content-Type": "application/json"},
			Body:       `{"error":"the response could not be encoded"}`,
		}, nil
	}
	return events.APIGatewayV2HTTPResponse{
		StatusCode: status,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       string(encoded),
	}, nil
}

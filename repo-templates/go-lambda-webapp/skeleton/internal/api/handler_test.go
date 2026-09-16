package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"testing"
	"time"

	"github.com/aws/aws-lambda-go/events"

	"github.com/${{ .Repo.Organization }}/${{ .Repo.Name }}/internal/orders"
)

// memStore stands in for DynamoDB. failPut and failList make the error paths
// reachable without an AWS client.
type memStore struct {
	held     []*orders.Order
	failPut  bool
	failList bool
}

func (m *memStore) Put(_ context.Context, o *orders.Order) error {
	if m.failPut {
		return errors.New("the table is unreachable")
	}
	m.held = append(m.held, o)
	return nil
}

func (m *memStore) List(_ context.Context, limit int) ([]*orders.Order, error) {
	if m.failList {
		return nil, errors.New("the table is unreachable")
	}
	if len(m.held) > limit {
		return m.held[:limit], nil
	}
	return m.held, nil
}

func newHandler(s orders.Store) *Handler {
	return &Handler{Store: s, Release: "test", Log: slog.New(slog.NewTextHandler(io.Discard, nil))}
}

func request(method, path, body string, query map[string]string) events.APIGatewayV2HTTPRequest {
	req := events.APIGatewayV2HTTPRequest{RawPath: path, Body: body, QueryStringParameters: query}
	req.RequestContext.HTTP.Method = method
	return req
}

func decode(t *testing.T, res events.APIGatewayV2HTTPResponse, into any) {
	t.Helper()
	if err := json.Unmarshal([]byte(res.Body), into); err != nil {
		t.Fatalf("response body is not JSON: %v (body %q)", err, res.Body)
	}
}

func TestPlaceOrder(t *testing.T) {
	const valid = `{"customer":"ada","items":[{"sku":"SKU-1","qty":3,"price":2.50}]}`

	t.Run("a valid order is stored and comes back with an id, a total and a timestamp", func(t *testing.T) {
		store := &memStore{}
		res, err := newHandler(store).Handle(context.Background(), request(http.MethodPost, "/api/orders", valid, nil))
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if res.StatusCode != http.StatusCreated {
			t.Fatalf("want 201, got %d (body %q)", res.StatusCode, res.Body)
		}

		var got orders.Order
		decode(t, res, &got)
		if got.OrderID == "" {
			t.Error("want an order id, got none")
		}
		if got.Total != 7.50 {
			t.Errorf("want total 7.50, got %v", got.Total)
		}
		if got.ReceivedAt.IsZero() {
			t.Error("want a received_at, got the zero time")
		}
		if len(store.held) != 1 {
			t.Fatalf("want 1 order stored, got %d", len(store.held))
		}
	})

	// Without this, a client could name its own id and overwrite another
	// customer's order.
	t.Run("an id, total and timestamp sent by the client are replaced", func(t *testing.T) {
		body := `{"order_id":"chosen-by-the-client","customer":"ada","total":0.01,` +
			`"received_at":"2000-01-01T00:00:00Z","items":[{"sku":"SKU-1","qty":3,"price":2.50}]}`
		res, _ := newHandler(&memStore{}).Handle(context.Background(), request(http.MethodPost, "/api/orders", body, nil))

		var got orders.Order
		decode(t, res, &got)
		if got.OrderID == "chosen-by-the-client" {
			t.Error("the client's order id was kept")
		}
		if got.Total != 7.50 {
			t.Errorf("want total 7.50, got %v", got.Total)
		}
		if got.ReceivedAt.Year() == 2000 {
			t.Error("the client's received_at was kept")
		}
	})

	t.Run("a body that is not JSON is a 400 and stores nothing", func(t *testing.T) {
		store := &memStore{}
		res, _ := newHandler(store).Handle(context.Background(), request(http.MethodPost, "/api/orders", "not json", nil))
		if res.StatusCode != http.StatusBadRequest {
			t.Fatalf("want 400, got %d", res.StatusCode)
		}
		if len(store.held) != 0 {
			t.Errorf("want nothing stored, got %d", len(store.held))
		}
	})

	t.Run("an order that breaks a rule is a 400 carrying the reason", func(t *testing.T) {
		res, _ := newHandler(&memStore{}).Handle(context.Background(), request(http.MethodPost, "/api/orders", `{"items":[]}`, nil))
		if res.StatusCode != http.StatusBadRequest {
			t.Fatalf("want 400, got %d", res.StatusCode)
		}
		var got map[string]string
		decode(t, res, &got)
		if got["error"] != "customer is required" {
			t.Errorf("want the reason from Validate, got %q", got["error"])
		}
	})

	t.Run("a store that cannot write is a 500, not a 201", func(t *testing.T) {
		res, _ := newHandler(&memStore{failPut: true}).Handle(context.Background(), request(http.MethodPost, "/api/orders", valid, nil))
		if res.StatusCode != http.StatusInternalServerError {
			t.Fatalf("want 500, got %d (body %q)", res.StatusCode, res.Body)
		}
	})
}

func TestListOrders(t *testing.T) {
	stocked := func(n int) *memStore {
		s := &memStore{}
		for i := 0; i < n; i++ {
			s.held = append(s.held, &orders.Order{OrderID: string(rune('a' + i)), ReceivedAt: time.Now()})
		}
		return s
	}

	t.Run("an empty table returns an empty list, not a null", func(t *testing.T) {
		res, _ := newHandler(&memStore{}).Handle(context.Background(), request(http.MethodGet, "/api/orders", "", nil))
		if res.StatusCode != http.StatusOK {
			t.Fatalf("want 200, got %d", res.StatusCode)
		}
		// `"orders":null` would make the SPA's .map() throw rather than render
		// an empty table.
		if res.Body != `{"orders":[]}` {
			t.Errorf("want an empty array, got %q", res.Body)
		}
	})

	t.Run("limit caps the number returned", func(t *testing.T) {
		res, _ := newHandler(stocked(5)).Handle(context.Background(), request(http.MethodGet, "/api/orders", "", map[string]string{"limit": "2"}))
		var got struct {
			Orders []*orders.Order `json:"orders"`
		}
		decode(t, res, &got)
		if len(got.Orders) != 2 {
			t.Fatalf("want 2 orders, got %d", len(got.Orders))
		}
	})

	t.Run("a limit above the maximum is clamped rather than refused", func(t *testing.T) {
		store := stocked(3)
		res, _ := newHandler(store).Handle(context.Background(), request(http.MethodGet, "/api/orders", "", map[string]string{"limit": "100000"}))
		if res.StatusCode != http.StatusOK {
			t.Fatalf("want 200, got %d (body %q)", res.StatusCode, res.Body)
		}
	})

	t.Run("a limit that is not a positive number is a 400", func(t *testing.T) {
		for _, raw := range []string{"nope", "0", "-1"} {
			res, _ := newHandler(&memStore{}).Handle(context.Background(), request(http.MethodGet, "/api/orders", "", map[string]string{"limit": raw}))
			if res.StatusCode != http.StatusBadRequest {
				t.Errorf("limit=%q: want 400, got %d", raw, res.StatusCode)
			}
		}
	})

	t.Run("a store that cannot read is a 500", func(t *testing.T) {
		res, _ := newHandler(&memStore{failList: true}).Handle(context.Background(), request(http.MethodGet, "/api/orders", "", nil))
		if res.StatusCode != http.StatusInternalServerError {
			t.Fatalf("want 500, got %d", res.StatusCode)
		}
	})
}

func TestRouting(t *testing.T) {
	h := newHandler(&memStore{})

	t.Run("healthz reports the release the function is running", func(t *testing.T) {
		res, _ := h.Handle(context.Background(), request(http.MethodGet, "/api/healthz", "", nil))
		var got map[string]string
		decode(t, res, &got)
		if got["release"] != "test" {
			t.Errorf("want release \"test\", got %q", got["release"])
		}
	})

	// A 404 here rather than a fallthrough is what keeps a mistyped API call
	// from coming back as the SPA's HTML.
	t.Run("an unknown path is a JSON 404", func(t *testing.T) {
		res, _ := h.Handle(context.Background(), request(http.MethodGet, "/api/nope", "", nil))
		if res.StatusCode != http.StatusNotFound {
			t.Fatalf("want 404, got %d", res.StatusCode)
		}
		if res.Headers["Content-Type"] != "application/json" {
			t.Errorf("want a JSON content type, got %q", res.Headers["Content-Type"])
		}
	})

	t.Run("a method the endpoint does not serve is a 405", func(t *testing.T) {
		res, _ := h.Handle(context.Background(), request(http.MethodDelete, "/api/orders", "", nil))
		if res.StatusCode != http.StatusMethodNotAllowed {
			t.Fatalf("want 405, got %d", res.StatusCode)
		}
	})
}

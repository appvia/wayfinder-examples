package api

import (
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"

	"github.com/${{ .Repo.Organization }}/${{ .Repo.Name }}/internal/orders"
)

// attributevalue does not read json tags, so a field without a dynamodbav tag
// lands under its Go name. Without this test the table rejects every write for
// not supplying order_id, and nothing else in the suite notices.
func TestOrderMarshalsToTheAttributeNamesTheTableExpects(t *testing.T) {
	item, err := attributevalue.MarshalMap(&orders.Order{
		OrderID:    "order-1",
		Customer:   "ada",
		Items:      []orders.Item{{SKU: "SKU-1", Qty: 2, Price: 1.5}},
		Total:      3,
		ReceivedAt: time.Now().UTC(),
	})
	if err != nil {
		t.Fatalf("marshalling an order: %v", err)
	}

	for _, want := range []string{"order_id", "customer", "items", "total", "received_at"} {
		if _, ok := item[want]; !ok {
			t.Errorf("no %q attribute; got %v", want, keys(item))
		}
	}
}

func keys[V any](m map[string]V) []string {
	out := make([]string, 0, len(m))
	for k := range m {
		out = append(out, k)
	}
	return out
}

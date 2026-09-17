package orders

import "testing"

func TestValidate(t *testing.T) {
	good := []Item{{SKU: "SKU-1", Qty: 2, Price: 9.99}}

	cases := map[string]struct {
		order Order
		want  string
	}{
		"a complete order is accepted": {
			order: Order{Customer: "ada", Items: good},
		},
		"an order with no customer is rejected": {
			order: Order{Items: good},
			want:  "customer is required",
		},
		"an order with no items is rejected": {
			order: Order{Customer: "ada"},
			want:  "an order needs at least one item",
		},
		"an item with no sku is rejected": {
			order: Order{Customer: "ada", Items: []Item{{Qty: 1, Price: 1}}},
			want:  "items[0]: sku is required",
		},
		"an item with zero quantity is rejected": {
			order: Order{Customer: "ada", Items: []Item{{SKU: "SKU-1", Qty: 0, Price: 1}}},
			want:  "items[0]: qty must be at least 1, got 0",
		},
		"an item with a negative price is rejected": {
			order: Order{Customer: "ada", Items: []Item{{SKU: "SKU-1", Qty: 1, Price: -1}}},
			want:  "items[0]: price cannot be negative, got -1",
		},
	}

	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			err := tc.order.Validate()
			switch {
			case tc.want == "" && err != nil:
				t.Fatalf("want no error, got %v", err)
			case tc.want != "" && err == nil:
				t.Fatalf("want error %q, got none", tc.want)
			case tc.want != "" && err.Error() != tc.want:
				t.Fatalf("want error %q, got %q", tc.want, err)
			}
		})
	}
}

// Totalise is what stops a client stating its own total, so a submitted Total
// has to be overwritten rather than merely filled in when absent.
func TestTotaliseOverwritesASubmittedTotal(t *testing.T) {
	o := Order{
		Customer: "ada",
		Items:    []Item{{SKU: "SKU-1", Qty: 3, Price: 2.50}, {SKU: "SKU-2", Qty: 1, Price: 0.50}},
		Total:    0.01,
	}
	o.Totalise()
	if o.Total != 8.00 {
		t.Fatalf("want total 8.00, got %v", o.Total)
	}
}

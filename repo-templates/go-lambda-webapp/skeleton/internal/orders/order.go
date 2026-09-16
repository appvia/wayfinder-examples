// Package orders holds the order record, the rules an order must satisfy, and
// the storage contract the API is written against. Nothing here talks to AWS:
// keeping the rules free of a client is what lets order_test.go run without
// one.
package orders

import (
	"context"
	"errors"
	"fmt"
	"time"
)

// Item is one line of an order.
//
// The dynamodbav tags repeat the json ones because attributevalue.MarshalMap
// does not read json tags. Without them the attribute would be named after the
// Go field, and a write would be rejected for not supplying order_id — the
// table's hash key.
type Item struct {
	SKU   string  `json:"sku"   dynamodbav:"sku"`
	Qty   int     `json:"qty"   dynamodbav:"qty"`
	Price float64 `json:"price" dynamodbav:"price"`
}

// Order is one customer order.
type Order struct {
	OrderID    string    `json:"order_id"    dynamodbav:"order_id"`
	Customer   string    `json:"customer"    dynamodbav:"customer"`
	Items      []Item    `json:"items"       dynamodbav:"items"`
	Total      float64   `json:"total"       dynamodbav:"total"`
	ReceivedAt time.Time `json:"received_at" dynamodbav:"received_at"`
}

// ErrNotFound is returned by Store.Get when no order has that id.
var ErrNotFound = errors.New("order not found")

// Validate reports the first thing wrong with the order, in the words the
// caller sees in the 400 body.
func (o *Order) Validate() error {
	if o.Customer == "" {
		return errors.New("customer is required")
	}
	if len(o.Items) == 0 {
		return errors.New("an order needs at least one item")
	}
	for i, it := range o.Items {
		if it.SKU == "" {
			return fmt.Errorf("items[%d]: sku is required", i)
		}
		if it.Qty < 1 {
			return fmt.Errorf("items[%d]: qty must be at least 1, got %d", i, it.Qty)
		}
		if it.Price < 0 {
			return fmt.Errorf("items[%d]: price cannot be negative, got %v", i, it.Price)
		}
	}
	return nil
}

// Totalise sets Total from the items, so a client cannot state its own total
// and have it believed.
func (o *Order) Totalise() {
	var total float64
	for _, it := range o.Items {
		total += it.Price * float64(it.Qty)
	}
	o.Total = total
}

// Store is what the API needs of a database. The DynamoDB implementation is in
// internal/api; the tests use their own.
type Store interface {
	// Put writes an order, replacing any order with the same id.
	Put(ctx context.Context, o *Order) error
	// List returns up to limit orders, most recently received first.
	List(ctx context.Context, limit int) ([]*Order, error)
}

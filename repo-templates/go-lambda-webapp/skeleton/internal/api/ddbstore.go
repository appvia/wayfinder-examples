package api

import (
	"context"
	"fmt"
	"sort"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"

	"github.com/${{ .Repo.Organization }}/${{ .Repo.Name }}/internal/orders"
)

// DynamoStore keeps orders in the table the stack's `orders` component creates.
// It holds no credentials: the Lambda's own workload identity carries the
// read-write grant the manifest states, and the SDK picks it up from the
// execution role.
type DynamoStore struct {
	Client *dynamodb.Client
	Table  string
}

func (s *DynamoStore) Put(ctx context.Context, o *orders.Order) error {
	item, err := attributevalue.MarshalMap(o)
	if err != nil {
		return fmt.Errorf("encoding order %s: %w", o.OrderID, err)
	}
	if _, err := s.Client.PutItem(ctx, &dynamodb.PutItemInput{
		TableName: aws.String(s.Table),
		Item:      item,
	}); err != nil {
		return fmt.Errorf("writing order %s to %s: %w", o.OrderID, s.Table, err)
	}
	return nil
}

// List scans the table and sorts in the process. The table is keyed by order_id
// alone, so there is no index to read "most recent first" from, and a scan is
// honest at demo volumes. Past a few thousand orders, add a global secondary
// index keyed on a coarse time bucket and query that instead.
func (s *DynamoStore) List(ctx context.Context, limit int) ([]*orders.Order, error) {
	out, err := s.Client.Scan(ctx, &dynamodb.ScanInput{TableName: aws.String(s.Table)})
	if err != nil {
		return nil, fmt.Errorf("scanning %s: %w", s.Table, err)
	}

	found := make([]*orders.Order, 0, len(out.Items))
	for _, item := range out.Items {
		var o orders.Order
		if err := attributevalue.UnmarshalMap(item, &o); err != nil {
			return nil, fmt.Errorf("decoding an order from %s: %w", s.Table, err)
		}
		found = append(found, &o)
	}

	sort.Slice(found, func(i, j int) bool { return found[i].ReceivedAt.After(found[j].ReceivedAt) })
	if len(found) > limit {
		found = found[:limit]
	}
	return found, nil
}

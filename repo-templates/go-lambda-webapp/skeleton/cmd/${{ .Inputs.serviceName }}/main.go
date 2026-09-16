// Command orders is the order intake API. It runs as a container on AWS Lambda
// behind API Gateway, and keeps orders in the DynamoDB table the stack's
// `orders` component creates.
//
// ORDERS_TABLE is set by Wayfinder.yaml from that component's output, and the
// Lambda reaches the table through its own workload identity — there are no
// credentials in this repository.
package main

import (
	"context"
	"log/slog"
	"os"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"

	"github.com/${{ .Repo.Organization }}/${{ .Repo.Name }}/internal/api"
)

// release is set at build time with -ldflags "-X main.release=...". CI passes
// the git tag for a release build and the short SHA otherwise, so a running
// function can always be traced back to a commit.
var release = "dev"

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	table := os.Getenv("ORDERS_TABLE")
	if table == "" {
		log.Error("ORDERS_TABLE is not set; Wayfinder.yaml sets it from the orders component")
		os.Exit(1)
	}

	cfg, err := config.LoadDefaultConfig(context.Background())
	if err != nil {
		log.Error("reading the AWS configuration", "error", err)
		os.Exit(1)
	}

	handler := &api.Handler{
		Store:   &api.DynamoStore{Client: dynamodb.NewFromConfig(cfg), Table: table},
		Release: release,
		Log:     log,
	}

	log.Info("starting", "release", release, "table", table)
	lambda.Start(handler.Handle)
}

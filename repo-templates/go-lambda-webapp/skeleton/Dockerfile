# syntax=docker/dockerfile:1.6
#
# The Lambda function's container image. BuildKit cache mounts keep Go's module
# and build caches between builds, so changing one file does not recompile the
# world — which is what makes the deploy loop worth using while iterating.

FROM golang:1.26 AS build
WORKDIR /src

COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    go mod download

ARG RELEASE=dev
COPY . .
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -tags lambda.norpc -ldflags "-s -w -X main.release=${RELEASE}" -o /bootstrap ./cmd/${{ .Inputs.serviceName }}

FROM public.ecr.aws/lambda/provided:al2023
COPY --from=build /bootstrap /var/runtime/bootstrap
CMD ["bootstrap"]

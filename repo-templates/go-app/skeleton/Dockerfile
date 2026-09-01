# syntax=docker/dockerfile:1

# ---- Build -----------------------------------------------------------------
FROM golang:1.25-alpine AS build

WORKDIR /src

# Dependencies first, so a source-only change reuses the cached module layer.
COPY go.mod go.su[m] ./
RUN go mod download

COPY . .

# VERSION is passed by CI: the git tag for a release, the short SHA otherwise.
# It is compiled in so a running pod can report exactly what it is.
ARG VERSION=dev
RUN CGO_ENABLED=0 GOOS=linux go build \
      -ldflags "-s -w -X main.release=${VERSION}" \
      -o /${{ .Inputs.serviceName }} \
      ./cmd/${{ .Inputs.serviceName }}

# ---- Run -------------------------------------------------------------------
# Static distroless: no shell, no package manager, and a non-root user by
# default. Nothing to exec into, which is the point.
FROM gcr.io/distroless/static-debian12:nonroot

COPY --from=build /${{ .Inputs.serviceName }} /${{ .Inputs.serviceName }}

USER nonroot:nonroot
EXPOSE ${{ .Inputs.port }}

ENTRYPOINT ["/${{ .Inputs.serviceName }}"]

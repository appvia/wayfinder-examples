# CI and the stack's `pushimage` component call `make test`, `make lint`,
# `make build`, `make image` and `make web` — nothing else. That is deliberate:
# how this service is built belongs here, not in .github/workflows/ or
# Wayfinder.yaml.
BINARY := ${{ .Inputs.serviceName }}
RELEASE ?= dev

.PHONY: all
all: lint test build web

.PHONY: test
test:
	go test ./... -race -coverprofile=coverage.out -covermode=atomic

.PHONY: lint
lint:
	@unformatted="$$(gofmt -l .)"; \
	if [ -n "$$unformatted" ]; then \
		echo "These files need gofmt:"; echo "$$unformatted"; exit 1; \
	fi
	go vet ./...

# The Lambda runtime runs a binary called bootstrap, whatever the command is
# called in this repository.
.PHONY: build
build:
	CGO_ENABLED=0 go build -tags lambda.norpc -ldflags "-s -w -X main.release=$(RELEASE)" -o bin/bootstrap ./cmd/$(BINARY)

.PHONY: image
image:
	DOCKER_BUILDKIT=1 docker build --platform linux/amd64 --provenance=false --sbom=false --load \
		--build-arg RELEASE=$(RELEASE) -t $(BINARY):$(RELEASE) .

# VITE_API_BASE is /api because CloudFront serves the web app and the API from
# one domain; the browser never learns the API Gateway URL.
.PHONY: web
web:
	npm --prefix web ci
	VITE_API_BASE=/api npm --prefix web run build

.PHONY: clean
clean:
	rm -rf bin coverage.out web/dist web/node_modules

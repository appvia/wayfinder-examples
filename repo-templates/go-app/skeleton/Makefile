# The CI workflows call `make test`, `make lint` and `make build` and nothing
# else. That is deliberate: it keeps the workflows identical across every
# Wayfinder service template, whatever the language. Change how this service is
# built here, not in .github/workflows/.
BINARY := ${{ .Inputs.serviceName }}
RELEASE ?= dev

.PHONY: all
all: lint test build

# Nothing to install: GitHub runners ship Go, and go.mod's `go 1.25` makes the
# toolchain fetch the right version by itself.
.PHONY: ci-setup
ci-setup:
	go version

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
# For a stricter check, install golangci-lint and add:
#	golangci-lint run

.PHONY: build
build:
	CGO_ENABLED=0 go build -ldflags "-s -w -X main.release=$(RELEASE)" -o bin/$(BINARY) ./cmd/$(BINARY)

.PHONY: run
run:
	go run ./cmd/$(BINARY)

.PHONY: image
image:
	docker build --build-arg VERSION=$(RELEASE) -t $(BINARY):$(RELEASE) .

.PHONY: clean
clean:
	rm -rf bin coverage.out

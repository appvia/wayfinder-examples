# The CI workflows call `make ci-setup`, `make test`, `make lint` and
# `make build` and nothing else. That is deliberate: it keeps the workflows
# identical across every Wayfinder service template, whatever the language.
# Change how this service is built here, not in .github/workflows/.
RELEASE ?= dev

.PHONY: all
all: lint test

# GitHub runners ship Node, so there is no toolchain to install — but the
# dependencies still need to be there before test or lint can run.
.PHONY: ci-setup
ci-setup:
	node --version
	npm ci

.PHONY: install
install:
	npm install

.PHONY: test
test:
	npm test

.PHONY: lint
lint:
	npm run typecheck
	npm run format:check

.PHONY: format
format:
	npm run format

.PHONY: build
build:
	npm run typecheck

.PHONY: run
run:
	npm start

.PHONY: image
image:
	docker build --build-arg VERSION=$(RELEASE) -t ${{ .Inputs.serviceName }}:$(RELEASE) .

.PHONY: clean
clean:
	rm -rf node_modules

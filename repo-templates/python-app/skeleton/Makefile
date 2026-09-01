# The CI workflows call `make ci-setup`, `make test`, `make lint` and
# `make build` and nothing else. That is deliberate: it keeps the workflows
# identical across every Wayfinder service template, whatever the language.
# Change how this service is built here, not in .github/workflows/.
RELEASE ?= dev

.PHONY: all
all: lint test

# GitHub runners ship Python but not uv, and uv manages the interpreter as well
# as the dependencies, so installing it is all the bootstrap this needs.
.PHONY: ci-setup
ci-setup:
	@command -v uv >/dev/null || curl -LsSf https://astral.sh/uv/install.sh | sh
	uv --version

.PHONY: install
install:
	uv sync --locked

.PHONY: test
test: install
	uv run pytest -q

.PHONY: lint
lint: install
	uv run ruff check .
	uv run ruff format --check .

.PHONY: format
format: install
	uv run ruff format .
	uv run ruff check --fix .

.PHONY: build
build: install
	uv build

.PHONY: run
run: install
	uv run uvicorn app.main:app --reload --port ${{ .Inputs.port }}

.PHONY: image
image:
	docker build --build-arg VERSION=$(RELEASE) -t ${{ .Inputs.serviceName }}:$(RELEASE) .

.PHONY: clean
clean:
	rm -rf .venv dist .pytest_cache .ruff_cache

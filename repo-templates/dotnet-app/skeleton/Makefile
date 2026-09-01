# The CI workflows call `make ci-setup`, `make test`, `make lint` and
# `make build` and nothing else. That is deliberate: it keeps the workflows
# identical across every Wayfinder service template, whatever the language.
# Change how this service is built here, not in .github/workflows/.
RELEASE ?= dev

APP := src/Service
TESTS := tests/Service.Tests

DOTNET := dotnet --nologo

.PHONY: all
all: lint test

# GitHub runners ship a .NET SDK, but not necessarily this major version. Install
# it only when it is missing, so a runner that already has it costs nothing.
.PHONY: ci-setup
ci-setup:
	@if $(DOTNET) --list-sdks 2>/dev/null | grep -q '^10\.'; then \
		echo "found .NET 10 SDK"; \
	else \
		echo "installing the .NET 10 SDK"; \
		curl -fsSL https://dot.net/v1/dotnet-install.sh -o /tmp/dotnet-install.sh; \
		bash /tmp/dotnet-install.sh --channel 10.0 --install-dir "$$HOME/.dotnet"; \
		echo "$$HOME/.dotnet" >> $${GITHUB_PATH:-/dev/null}; \
	fi
	$(DOTNET) --version

.PHONY: test
test:
	$(DOTNET) test $(TESTS)

# TreatWarningsAsErrors is set in both csproj files, so a warning fails this.
# `format --verify-no-changes` is the formatting half.
.PHONY: lint
lint:
	$(DOTNET) build $(APP) --configuration Release
	$(DOTNET) format $(APP) --verify-no-changes
	$(DOTNET) format $(TESTS) --verify-no-changes

.PHONY: format
format:
	$(DOTNET) format $(APP)
	$(DOTNET) format $(TESTS)

.PHONY: build
build:
	$(DOTNET) publish $(APP) --configuration Release -o ./publish

.PHONY: run
run:
	$(DOTNET) run --project $(APP)

.PHONY: image
image:
	docker build --build-arg VERSION=$(RELEASE) -t ${{ .Inputs.serviceName }}:$(RELEASE) .

.PHONY: clean
clean:
	rm -rf publish $(APP)/bin $(APP)/obj $(TESTS)/bin $(TESTS)/obj

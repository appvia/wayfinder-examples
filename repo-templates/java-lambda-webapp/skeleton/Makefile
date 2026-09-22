# CI and the stack's action components call `make test`, `make lint`,
# `make build`, `make image`, `make web` and `make smoke` — nothing else. That
# is deliberate: how this service is built and checked belongs here, not in
# .github/workflows/ or Wayfinder.yaml.
SERVICE := ${{ .Inputs.serviceName }}
RELEASE ?= dev

# GitHub runners ship several JDKs and default to an older one. JAVA_HOME_21_X64
# is set by the runner image; falling back to JAVA_HOME keeps this working on a
# laptop that has just the one.
export JAVA_HOME := $(if $(JAVA_HOME_21_X64),$(JAVA_HOME_21_X64),$(JAVA_HOME))

# No Maven wrapper: GitHub runners ship Maven and the image build uses a Maven
# base image. Add one with `mvn wrapper:wrapper` if your team prefers it.
MVN := mvn --batch-mode --no-transfer-progress

.PHONY: all
all: lint test build web

# Maven and a JDK are already on GitHub runners, so there is nothing to install.
.PHONY: ci-setup
ci-setup:
	$(MVN) --version

.PHONY: test
test:
	$(MVN) test

# Compiles with -Xlint:all -Werror (see pom.xml), so a warning fails the build.
.PHONY: lint
lint:
	$(MVN) --quiet compile

# The classes and the runtime jars the image copies, in target/classes and
# target/dependency. RELEASE is written into release.properties, which is what
# /api/healthz reports.
.PHONY: build
build:
	$(MVN) package -DskipTests -Drelease=$(RELEASE)

.PHONY: image
image:
	DOCKER_BUILDKIT=1 docker build --platform linux/amd64 --provenance=false --sbom=false --load \
		--build-arg RELEASE=$(RELEASE) -t $(SERVICE):$(RELEASE) .

# VITE_API_BASE is /api because CloudFront serves the web app and the API from
# one domain; the browser never learns the API Gateway URL.
.PHONY: web
web:
	npm --prefix web ci
	VITE_API_BASE=/api npm --prefix web run build

# Checks a deployed instance from outside: healthz reports ok, and an order
# placed through the API comes back when the orders are listed. Needs curl and
# jq and nothing else, so the sandbox an agent deploys from can run it.
#
# Pass EXPECT_ORDER_ID with an id from an earlier run to assert that order is
# still listed — that is how a deploy over an existing instance shows the data
# already there survived it. The last line is order_id=<id> so the next run can
# be given it.
#
#   make smoke URL=https://d111111abcdef8.cloudfront.net
#   make smoke URL=... EXPECT_ORDER_ID=6f1c...
.PHONY: smoke
smoke:
	@set -eu; \
	url="$(URL)"; \
	if [ -z "$$url" ]; then \
		echo "smoke: pass the instance URL, e.g. make smoke URL=https://d111111abcdef8.cloudfront.net" >&2; exit 1; \
	fi; \
	body="$$(mktemp)"; \
	trap 'rm -f "$$body"' EXIT; \
	code=000; \
	for attempt in 1 2 3 4 5 6; do \
		code="$$(curl -sS -m 20 -o "$$body" -w '%{http_code}' "$$url/api/healthz" || true)"; \
		if [ "$$code" = "200" ]; then break; fi; \
		echo "smoke: $$url/api/healthz answered $$code, waiting for the deploy to settle ($$attempt of 6)" >&2; \
		sleep 5; \
	done; \
	if [ "$$code" != "200" ]; then \
		echo "smoke: GET /api/healthz answered $$code:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	if [ "$$(jq -r '.status' <"$$body")" != "ok" ]; then \
		echo "smoke: GET /api/healthz did not report ok:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	echo "smoke: healthz ok, running release $$(jq -r '.release' <"$$body")"; \
	code="$$(curl -sS -m 30 -o "$$body" -w '%{http_code}' -X POST "$$url/api/orders" \
		-H 'Content-Type: application/json' \
		-d '{"customer":"smoke","items":[{"sku":"SMOKE-1","qty":2,"price":4.5}]}' || true)"; \
	if [ "$$code" != "201" ]; then \
		echo "smoke: POST /api/orders answered $$code:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	placed="$$(jq -r '.order_id' <"$$body")"; \
	if [ -z "$$placed" ] || [ "$$placed" = "null" ]; then \
		echo "smoke: POST /api/orders returned no order_id:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	echo "smoke: placed order $$placed for $$(jq -r '.total' <"$$body")"; \
	code="$$(curl -sS -m 30 -o "$$body" -w '%{http_code}' "$$url/api/orders?limit=200" || true)"; \
	if [ "$$code" != "200" ]; then \
		echo "smoke: GET /api/orders answered $$code:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	for wanted in "$$placed" "$(EXPECT_ORDER_ID)"; do \
		if [ -z "$$wanted" ]; then continue; fi; \
		if [ "$$(jq -r --arg id "$$wanted" 'any(.orders[]; .order_id == $$id)' <"$$body")" != "true" ]; then \
			echo "smoke: order $$wanted is not in GET /api/orders:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
		fi; \
		echo "smoke: order $$wanted is listed"; \
	done; \
	echo "order_id=$$placed"

.PHONY: clean
clean:
	rm -rf target web/dist web/node_modules

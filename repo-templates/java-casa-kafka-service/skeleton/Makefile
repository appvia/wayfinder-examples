# CI and the stack's action components call `make test`, `make lint`,
# `make image` and `make smoke` — nothing else. How this service is built and
# checked belongs here, not in .github/workflows/ or Wayfinder.yaml, so a change
# to the build is one change that CI, a deploy and your laptop all pick up.
SERVICE := ${{ .Inputs.serviceName }}
RELEASE ?= dev

# The two apps, each with its own Dockerfile. An image is <service>-<app>, which
# is also the name of the ECR repository it is pushed to.
APPS := message-service frontend

# GitHub runners ship several JDKs and default to an older one. JAVA_HOME_25_X64
# is set by the runner image; without it, the JAVA_HOME you already have is used,
# which keeps this working on a laptop with just the one JDK.
ifneq ($(JAVA_HOME_25_X64),)
export JAVA_HOME := $(JAVA_HOME_25_X64)
endif

# No Maven wrapper: GitHub runners ship Maven and the image build uses a Maven
# base image. Add one with `mvn wrapper:wrapper` if your team prefers it.
MVN := mvn --batch-mode --no-transfer-progress -f message-service/pom.xml

.PHONY: all
all: lint test

# Maven, a JDK and Node are already on GitHub runners, so there is nothing to
# install. This shows which versions a build is about to use.
.PHONY: ci-setup
ci-setup:
	$(MVN) --version
	node --version

# npm writes node_modules/.package-lock.json on every install, so this reruns
# `npm ci` only when package-lock.json has changed since the last one.
frontend/node_modules/.package-lock.json: frontend/package-lock.json
	npm --prefix frontend ci

.PHONY: test
test: frontend/node_modules/.package-lock.json
	$(MVN) test
	npm --prefix frontend test

.PHONY: lint
lint: frontend/node_modules/.package-lock.json
	$(MVN) --quiet compile
	npm --prefix frontend run lint

# One app's image, built from its own directory:
#
#   make image APP=message-service RELEASE=sha-1234567
#
# linux/amd64 because that is what the Fargate tasks run, whatever machine the
# build is on. No provenance or SBOM attestations: they turn the push into a
# manifest list, which is more than a single-platform task needs.
.PHONY: image
image:
	@case "$(APP)" in message-service|frontend) ;; \
		*) echo "image: pass APP=message-service or APP=frontend" >&2; exit 1 ;; esac
	DOCKER_BUILDKIT=1 docker build --platform linux/amd64 --provenance=false --sbom=false --load \
		--build-arg RELEASE=$(RELEASE) -t $(SERVICE)-$(APP):$(RELEASE) $(APP)

.PHONY: images
images:
	@for app in $(APPS); do $(MAKE) --no-print-directory image APP=$$app RELEASE=$(RELEASE) || exit 1; done

# Kafka, both apps and a proxy on :8000 that sends /api/* to message-service and
# everything else to the frontend, the way the load balancer does in AWS. So the
# same smoke check runs against your laptop and a deployed instance:
#
#   make up
#   make smoke URL=http://localhost:8000
.PHONY: up
up:
	RELEASE=$(RELEASE) docker compose up --build --detach
	@echo "up: the service is on http://localhost:8000 (Kafka on localhost:9094)"

# Kafka keeps nothing on disk, so the next `make up` starts with no topic and no
# messages.
.PHONY: down
down:
	docker compose down

# Checks a running instance from outside, through the one URL a user has:
#
#   1. the frontend answers /healthz, and the page renders;
#   2. message-service answers /api/healthz with ok, and names its release;
#   3. a message POSTed to /api/messages is accepted with a reference;
#   4. that message reads back as RECEIVED within about 90 seconds.
#
# Step 4 is the one that matters. message-service marks a message RECEIVED only
# when it consumes the MessageSubmitted record it published for it, so a message
# that stays SUBMITTED means the record never made it through Kafka: a broker the
# task cannot reach, a missing IAM grant, or an Avro schema the consumer refuses.
#
# Needs curl and jq and nothing else, so the sandbox an agent deploys from can
# run it.
#
#   make smoke URL=https://d111111abcdef8.cloudfront.net
#   make smoke URL=http://localhost:8000
.PHONY: smoke
smoke:
	@set -eu; \
	url="$(URL)"; \
	if [ -z "$$url" ]; then \
		echo "smoke: pass the instance URL, e.g. make smoke URL=http://localhost:8000" >&2; exit 1; \
	fi; \
	url="$${url%/}"; \
	body="$$(mktemp)"; \
	trap 'rm -f "$$body"' EXIT; \
	code=000; \
	for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do \
		code="$$(curl -sS -m 20 -o "$$body" -w '%{http_code}' "$$url/healthz" || true)"; \
		if [ "$$code" = "200" ]; then break; fi; \
		echo "smoke: $$url/healthz answered $$code, waiting for the load balancer to route to a healthy task ($$attempt of 12)" >&2; \
		sleep 10; \
	done; \
	if [ "$$code" != "200" ]; then \
		echo "smoke: GET /healthz answered $$code:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	echo "smoke: frontend healthz ok"; \
	code="$$(curl -sS -L -m 20 -o "$$body" -w '%{http_code}' "$$url/" || true)"; \
	if [ "$$code" != "200" ]; then \
		echo "smoke: GET / answered $$code:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	if ! grep -q '<title>Send a message' "$$body"; then \
		echo "smoke: GET / did not render the message page:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	echo "smoke: message page renders"; \
	code="$$(curl -sS -m 20 -o "$$body" -w '%{http_code}' "$$url/api/healthz" || true)"; \
	if [ "$$code" != "200" ] || [ "$$(jq -r '.status' <"$$body" 2>/dev/null)" != "ok" ]; then \
		echo "smoke: GET /api/healthz answered $$code and did not report ok:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	echo "smoke: message-service healthz ok, running release $$(jq -r '.release' <"$$body")"; \
	code="$$(curl -sS -m 30 -o "$$body" -w '%{http_code}' -X POST "$$url/api/messages" \
		-H 'Content-Type: application/json' -d '{"message":"Smoke test"}' || true)"; \
	if [ "$$code" != "201" ]; then \
		echo "smoke: POST /api/messages answered $$code:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	ref="$$(jq -r '.reference // empty' <"$$body")"; \
	if [ -z "$$ref" ]; then \
		echo "smoke: POST /api/messages returned no reference:" >&2; cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	echo "smoke: sent message $$ref"; \
	status=; \
	for attempt in $$(seq 1 18); do \
		code="$$(curl -sS -m 20 -o "$$body" -w '%{http_code}' "$$url/api/messages/$$ref" || true)"; \
		status="$$(jq -r '.status // empty' <"$$body" 2>/dev/null || true)"; \
		if [ "$$code" = "200" ] && [ "$$status" = "RECEIVED" ]; then break; fi; \
		echo "smoke: message $$ref is $${status:-unreadable (HTTP $$code)}, waiting for its record to be consumed ($$attempt of 18)" >&2; \
		sleep 5; \
	done; \
	if [ "$$status" != "RECEIVED" ]; then \
		echo "smoke: message $$ref never reached RECEIVED, so its record did not go through Kafka. Last answer (HTTP $$code):" >&2; \
		cat "$$body" >&2; echo >&2; exit 1; \
	fi; \
	echo "smoke: message $$ref is RECEIVED, so its record went through Kafka and was consumed"

.PHONY: clean
clean:
	rm -rf message-service/target frontend/node_modules

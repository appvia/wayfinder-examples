# The CI workflows call `make ci-setup`, `make test`, `make lint` and
# `make build` and nothing else. That is deliberate: it keeps the workflows
# identical across every Wayfinder service template, whatever the language.
# Change how this service is built here, not in .github/workflows/.
RELEASE ?= dev

# GitHub runners ship several JDKs and default to an older one. JAVA_HOME_21_X64
# is set by the runner image; falling back to JAVA_HOME keeps this working on a
# laptop that has just the one.
export JAVA_HOME := $(if $(JAVA_HOME_21_X64),$(JAVA_HOME_21_X64),$(JAVA_HOME))

# No Maven wrapper: GitHub runners ship Maven and the container build uses a
# Maven base image, so a wrapper would only be one more thing to keep current.
# Add one with `mvn wrapper:wrapper` if your team prefers it.
MVN := mvn --batch-mode --no-transfer-progress

.PHONY: all
all: lint test

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

.PHONY: build
build:
	$(MVN) package -DskipTests

.PHONY: run
run:
	$(MVN) spring-boot:run

.PHONY: image
image:
	docker build --build-arg VERSION=$(RELEASE) -t ${{ .Inputs.serviceName }}:$(RELEASE) .

.PHONY: clean
clean:
	$(MVN) clean

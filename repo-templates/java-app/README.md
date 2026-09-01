# Java Spring Boot service template

A Java service on Spring Boot, built with Maven on Java 21, with a Wayfinder
stack that runs it on Kubernetes alongside an S3 bucket it reaches through
workload identity.

This directory is the template definition. It is not itself a Java project — the
project lives under `skeleton/`, and only that is written into a new repository.

The package is a fixed `com.example.app` rather than one derived from the service
name. Renaming a package is a single IDE refactor; templating one means threading
it through every file, the build and the container image for no real gain.

There is no Maven wrapper: GitHub runners ship Maven and a JDK, and the container
build uses a Maven base image. Add one with `mvn wrapper:wrapper` if you want it.

| File | What it is |
| --- | --- |
| `wayfinder-template.yaml` | The template: its inputs, and which files are copied raw. |
| `RepoTemplate-java-app.yaml` | Registers the template with Wayfinder. |
| `skeleton/` | Everything written into the generated repository. |

## Using it

```bash
wf apply -f RepoTemplate-java-app.yaml
wf create stack payments --from-template java-app --input serviceName=payments --dry-run
```

Drop `--dry-run` and add `--github-org` and `--workspace` to create the
repository for real. See [../DELIVERY-PIPELINE.md](../DELIVERY-PIPELINE.md) for what CI does and what it needs first.

## What the generated repository contains

```
payments/
├── README.md
├── Wayfinder.yaml              app + S3 bucket, wired by workload identity
├── Makefile                    ci-setup / test / lint / build — what CI calls
├── Dockerfile                  layered build, eclipse-temurin:21-jre, non-root
├── pom.xml
├── src/main/java/com/example/app/
├── src/main/resources/application.yaml
├── src/test/java/com/example/app/
├── charts/app/                 the workload chart
├── plans/                      the CloudResourcePlan for the bucket
├── .wayfinder/                 ci.env (this repo's identity) + ci-env.sh
└── .github/workflows/          ci · preview teardown · deploy develop · deploy prod
```

## Inputs

`serviceName` is the only required one. It is used for the package name,
the image name, the Helm release and the stack instance names.

Everything else is optional and defaulted, so `--input serviceName=payments
--no-input` is enough to scaffold from CI. The two worth setting deliberately:

- `gatewayName` — name an existing Gateway API Gateway to publish the service.
  Left blank, the service deploys but is reachable in-cluster only.
- `workspace` — leave blank to take the workspace from the `WF_WORKSPACE`
  GitHub variable instead, which is better when one workspace serves many
  repositories.

Run `wf get repotemplate java-app -o yaml` to see the full list with defaults.

## Editing this template

- **`skeleton/.github/workflows/*` and `skeleton/charts/**` are raw.** They are
  copied byte for byte and never rendered, so they must not contain `${{ }}` or
  `$${{ }}` meant for the scaffolder. Per-repository values belong in
  `skeleton/.wayfinder/ci.env`, which is rendered.
- **`skeleton/Wayfinder.yaml` is rendered and mixes two engines.** Every
  Wayfinder *stack* expression in it must be written `$${{ ... }}` so it
  survives scaffolding as a literal `${{ ... }}`. Getting this wrong fails at
  the moment someone creates a repository, so check it with `--dry-run`.
- The workflows are shared across all four templates in this repository. Edit
  `../_shared/workflows/`, copy the result into every template, and CI will
  check they match.

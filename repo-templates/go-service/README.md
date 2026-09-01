# Go service with Helm chart template

A Go HTTP service that ships its own Helm chart and a deployable
`Wayfinder.yaml`, so the repository it creates can be deployed without anything
else being published first.

Register it:

```bash
wf apply -f repo-templates/go-service/RepoTemplate-go-service.yaml
```

`RepoTemplate-go-service.yaml` is the object you give Wayfinder — a pointer at
this directory. `wayfinder-template.yaml` is the template itself, which
Wayfinder reads once it has followed that pointer. Neither is scaffolded; only
`skeleton/` is. See [../ANATOMY.md](../ANATOMY.md).

## Inputs

| Input | Type | Required | Default | Notes |
| ----- | ---- | -------- | ------- | ----- |
| `serviceName` | string | yes | — | `^[a-z][a-z0-9]{1,19}$`, 2–20 chars |
| `summary` | string | no | — | One line, used in the README and the stack description |
| `goVersion` | string | no | `1.25` | One of `1.24`, `1.25` |
| `port` | number | no | `8080` | 1024–65535 |
| `replicas` | number | no | `2` | 1–10 |
| `includeDocs` | bool | no | `false` | When `true`, a `docs/` directory is generated |

## What the skeleton exercises

- **`$${{` escaping** — the CI workflow keeps GitHub Actions' own `${{ }}`
  expressions by escaping each one, rather than exempting the whole file. That
  leaves the rest of the workflow templatable, so the Go version and the service
  name are filled in for you. Contrast
  [`../go-microservice`](../go-microservice), which uses a `raw` glob instead.
- **A chart deployed with `chartPath`** — `Wayfinder.yaml` points at
  `./charts/app` in the same repository, so there is no chart to publish
  anywhere first.
- **Templated path** — `cmd/${{ .Inputs.serviceName }}/main.go`.
- **Conditional inclusion** — the condition wraps the whole path, so it can
  render to nothing and leave the file out.

## Deploying what it scaffolds

The generated `Wayfinder.yaml` deploys the chart and takes its image from
`${image}`, resolved by `wf up` from your shell or CI environment:

```bash
image=ghcr.io/your-org/your-repo:latest wf up
```

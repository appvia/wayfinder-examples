# Go microservice template

The worked example the Wayfinder documentation walks through file by file in
[Example Template Repository](https://on.wayfinder.run/docs/repo-templates/04-example-template-repository).

Register it:

```bash
wf apply -f repo-templates/go-microservice/RepoTemplate-go-microservice.yaml
```

or imperatively, which does the same thing:

```bash
wf create repotemplate go-microservice \
  --url https://github.com/appvia/wayfinder-examples.git \
  --path repo-templates/go-microservice \
  --category service --label language:go
```

`RepoTemplate-go-microservice.yaml` is the object you give Wayfinder — a pointer
at this directory. `wayfinder-template.yaml` is the template itself, which
Wayfinder reads once it has followed that pointer. Neither is scaffolded; only
`skeleton/` is. See [../ANATOMY.md](../ANATOMY.md).

## Inputs

| Input | Type | Required | Default | Notes |
| ----- | ---- | -------- | ------- | ----- |
| `serviceName` | string | yes | — | `^[a-z][a-z0-9-]*$`, 2–40 chars |
| `description` | string | no | `A Go microservice scaffolded by Wayfinder` | |
| `includeDocs` | bool | no | `false` | When `true`, a `docs/` directory is generated |

## What the skeleton exercises

- **Templated contents** — `${{ .Inputs.* }}`, `${{ .Stack.Name }}`, `${{ .Repo.* }}`.
- **Templated path** — `cmd/${{ .Inputs.serviceName }}/main.go`.
- **Conditional inclusion** — `${{ if .Inputs.includeDocs }}docs/index.md${{ end }}`,
  where the condition wraps the whole path so it can render to nothing.
- **Raw glob** — `.github/workflows/*.yaml` passes through unrendered, so GitHub
  Actions' native `${{ }}` expressions survive. Contrast
  [`../go-service`](../go-service), which escapes them with `$${{` instead so the
  workflow itself can still be templated.

## Deploying what it scaffolds

The generated `Wayfinder.yaml` declares a single `CloudResource` using the
`aws-s3-data-bucket` plan from [`../../quickstart/aws/plans`](../../quickstart/aws/plans),
so apply those plans first if you want to deploy it:

```bash
wf apply -f ./quickstart/aws/plans
```

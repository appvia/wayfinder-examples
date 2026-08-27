# ${{ .Inputs.serviceName }}

${{ .Inputs.summary | default "A Go HTTP service." }}

Created from the `go-service` repository template in Wayfinder. See
`wayfinder-stack.yaml` for the template and inputs it was created from.

- Repository: ${{ .Repo.URL }}
- Stack: `${{ .Stack.Name }}`
- Listens on: `:${{ .Inputs.port }}`

## Run it locally

```bash
go run ./cmd/${{ .Inputs.serviceName }}
curl localhost:${{ .Inputs.port }}/healthz
```

## Deploy it

The `Wayfinder.yaml` in this repository deploys the service to Kubernetes using
the Helm chart in `charts/app`. It needs a container image, which CI builds and
pushes on every merge to the default branch.

```bash
# Deploy a specific image into your dev environment
image=ghcr.io/${{ .Repo.Organization }}/${{ .Repo.Name }}:latest wf up
```

Wayfinder resolves `${image}` from your shell (or your CI environment) when it
deploys — see the [deployment docs](https://on.wayfinder.run/docs/for-app-teams/03-deploying-stacks).

## Layout

| Path | What it is |
|------|------------|
| `cmd/${{ .Inputs.serviceName }}/` | The service entrypoint |
| `charts/app/` | The Helm chart deployed by `Wayfinder.yaml` |
| `Wayfinder.yaml` | What Wayfinder deploys, and how |
| `.github/workflows/ci.yaml` | Build, test and publish the image |

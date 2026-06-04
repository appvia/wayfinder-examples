# `azure-blob-hello` — the quick-start container image

This is the source for the public image the Azure quick start runs:

```
ghcr.io/appvia/wayfinder-examples/azure-blob-hello:1.0.0
```

It's deliberately tiny and open so you can see exactly what runs in your
subscription. On each HTTP request it:

1. authenticates as the **Wayfinder-provisioned managed identity** attached to the
   container (via `DefaultAzureCredential`, which reads `AZURE_CLIENT_ID`),
2. **writes** a small blob to the storage account/container named by
   `DATA_ACCOUNT` / `DATA_CONTAINER`,
3. **lists** the container, and
4. returns the result as JSON.

It holds **no stored credentials** — that's the whole point of the quick start.
The only Azure-specific code is the data-plane access (`azure-identity` +
`azure-storage-blob`); everything else is the Python standard library, so this is
an ordinary container that would run anywhere (ACI, Container Apps, AKS, locally).

## Environment

| Variable          | Set by                       | Purpose                                          |
| ----------------- | ---------------------------- | ------------------------------------------------ |
| `AZURE_CLIENT_ID` | the plan (workload identity) | selects which assigned identity to authenticate as |
| `DATA_ACCOUNT`    | the manifest (wired input)   | storage account to read/write                    |
| `DATA_CONTAINER`  | the manifest (wired input)   | blob container to read/write                     |
| `PORT`            | optional (default `80`)      | port the HTTP server listens on                  |

## Build & run locally

```bash
docker build -t azure-blob-hello .
# Locally you'd supply credentials some other way; in Wayfinder the managed
# identity is injected automatically.
docker run --rm -p 8080:80 \
  -e DATA_ACCOUNT=<account> -e DATA_CONTAINER=<container> \
  azure-blob-hello
```

## Publishing

Pushed to GHCR by `.github/workflows/publish-azure-quickstart-image.yml` whenever
this directory changes on the default branch (and on `vN.N.N` tags). The package
must be made **public** once in the repo/org package settings so Container
Instances can pull it without credentials.

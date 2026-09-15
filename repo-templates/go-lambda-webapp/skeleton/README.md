# ${{ .Inputs.serviceName }}

${{ .Inputs.description }}

A Go API running as a container on AWS Lambda, a DynamoDB table behind it, and a
single-page web app that talks to it. There is no cluster to look after and no
IAM written by hand: the stack says which component may reach which, and
Wayfinder turns that into the workload's own permissions.

## Status

This repository was created from a template that is still being built. Right now
it holds this README and nothing else — the application, its `Wayfinder.yaml` and
its delivery pipeline are being written here first, and folded back into the
template once they work.

## What will be here

| | |
| --- | --- |
| `cmd/${{ .Inputs.serviceName }}/` | The Go handler, built as a container image |
| `web/` | The single-page app |
| `Wayfinder.yaml` | The stack: the table, the function, the gateway in front of it, and the web app |
| `.github/workflows/` | A preview environment per pull request, `develop` on merge, production on a tag |

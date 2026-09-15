# Go Lambda web app template

A team's starting point for a service that runs on AWS Lambda rather than on a
cluster: a Go API in a container behind API Gateway, a DynamoDB table, and a
single-page web app served by CloudFront.

It is the same delivery pipeline as the other golden paths — see
[../DELIVERY-PIPELINE.md](../DELIVERY-PIPELINE.md) — so a pull request gets its
own preview of the whole stack, merging deploys `develop`, and a `v*` tag deploys
production.

**Why this one exists beside [`go-app`](../go-app):** `go-app` deploys a Helm
chart and needs a cluster to deploy it to. A team whose cloud account was vended
for them has an account, a deployment identity and somewhere to keep Terraform
state — and no cluster. This template is for that team.

## Status: skeleton

It renders a README and nothing else. The application and its stack are being
built in a repository created from it, and will be back-filled here.

That order is deliberate. A template is a repository with holes in it, so a
failure in one has two possible causes — the code, or the render. Building the
application somewhere those are separable means a broken deploy is a broken
deploy. The render is only written once; the application is iterated on many
times.

| File | What it is |
| --- | --- |
| `wayfinder-template.yaml` | The template: its inputs, and which files are copied raw. |
| `RepoTemplate-go-lambda-webapp.yaml` | Registers the template with Wayfinder. |
| `skeleton/` | Everything written into the generated repository. |

See [../ANATOMY.md](../ANATOMY.md) for which of those two YAML files does what.

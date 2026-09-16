# ${{ .Inputs.serviceName }}

${{ .Inputs.description }}

A Go API running as a container on AWS Lambda, a DynamoDB table behind it, and a
single-page web app that talks to it. There is no cluster to look after and no
IAM written by hand: the stack says which component may reach which, and
Wayfinder turns that into the workload's own permissions.

## Layout

| | |
| --- | --- |
| `cmd/${{ .Inputs.serviceName }}/` | The Lambda entry point |
| `internal/orders/` | The order record and the rules it must satisfy |
| `internal/api/` | The HTTP handlers and the DynamoDB store behind them |
| `web/` | The single-page app |
| `Wayfinder.yaml` | The stack: the table, the registry, the function, the gateway and the web app |
| `plans/` | The CloudResourcePlans the stack deploys. Yours to edit |
| `.wayfinder/` | `ci.env` (this repository's identity) and the scripts CI runs |
| `.github/workflows/` | A preview per pull request, `develop` on merge, production on a tag |

## The stack

Seven components, deployed in this order:

```
db ─────────────┐
registry ─► pushimage ─► api ─► apigateway ─► web ─► deployweb
```

- **db** — the DynamoDB table. `api` is granted `read-write` on it and nothing
  else is granted anything, so the IAM policy on the function's role says
  exactly that.
- **registry** — this service's own ECR, created and destroyed with the rest of
  the stack. Every preview gets one of its own.
- **pushimage** — builds the container image and pushes it. It runs with the
  deployment identity's own credentials, so there is no registry credential
  stored anywhere.
- **api** — the Lambda function.
- **apigateway** — one `ANY /api/{proxy+}` route to the function, which routes
  requests itself. Adding an endpoint is a change to Go and nothing else.
- **web** — CloudFront, serving the web app on `/*` and forwarding `/api/*` to
  the gateway, so the browser talks to one origin.
- **deployweb** — builds the web app and puts it in the bucket CloudFront
  serves.

## Deploying it yourself

You need `docker`, `make`, `go`, `node` and the `aws` CLI: `pushimage` and
`deployweb` run on your machine, not in Wayfinder.

```bash
wf up -f Wayfinder.yaml -i ${{ .Inputs.serviceName }}-dev \
  -w <workspace> -e <environment> \
  --identity <workspace>/<environment>/aws-<environment> --region <region> \
  --env-var RELEASE=$(git rev-parse --short HEAD)
```

`RELEASE` tags the image and is compiled into the binary, so `/api/healthz` on
the running function names the commit it came from. Give it a new value when you
want Lambda to pull a new image — it caches aggressively by tag.

Tear it down with `wf down -i ${{ .Inputs.serviceName }}-dev -e <environment>`.

## The delivery pipeline

| When | What happens |
| --- | --- |
| A pull request opens or updates | Tests, a dry run, then a preview instance `${{ .Inputs.serviceName }}-pr<number>` whose URL is commented on the pull request |
| The pull request closes | The preview instance is destroyed |
| A merge to `main` | `${{ .Inputs.serviceName }}-develop` is deployed |
| A `v*` tag is pushed | `${{ .Inputs.serviceName }}-prod` is deployed, behind the `production` GitHub environment |

CI never logs into a registry. The stack creates its own ECR and pushes to it,
so there is no registry address to know before the first deploy and no registry
credential to store. The deploy jobs therefore run on a plain ubuntu runner
rather than in a container: `pushimage` and `deployweb` need docker, make, go
and node, which the runner already has.

### What CI needs

Repository or organisation variables:

| Variable | |
| --- | --- |
| `WF_SERVER` | Your Wayfinder API URL |
| `WF_REGION` | The AWS region to deploy into, e.g. `eu-west-2` |
| `WF_IDENTITY` | Optional. Defaults to `<workspace>/<environment>/aws-<environment>`, which is what an account vend creates |
| `WF_DNS_ZONE` | Optional. Without one the web app answers on CloudFront's own `*.cloudfront.net` domain |
| `WF_WORKSPACE` | Only when `.wayfinder/ci.env` leaves the workspace blank |

Three service accounts, one per environment, each trusting a different GitHub
OIDC subject so a credential a pull request can use cannot reach production:
`${{ .Inputs.serviceName }}-ci-preview`, `${{ .Inputs.serviceName }}-ci-develop`
and `${{ .Inputs.serviceName }}-ci-prod`. `repo-templates/setup-ci-service-accounts.sh`
in appvia/wayfinder-examples creates them.

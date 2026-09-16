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
| `Wayfinder.yaml` | The stack: the table, the image, the function, the gateway, the web app and a check that it answers |
| `plans/` | The CloudResourcePlans the stack deploys. Yours to edit |
| `.wayfinder/` | `ci.env` (the service name and the `wf` build CI installs), the script CI runs, and the two the stack's actions run |
| `.github/workflows/` | A preview per pull request, `develop` on merge, production on a tag |

## The stack

Seven components, deployed in this order:

```
db ───────────────┐
                  ▼
pushimage ──────► api ─► apigateway ─► web ─► deployweb ─► smoke
```

- **db** — the DynamoDB table. `api` is granted `read-write` on it and nothing
  else is granted anything, so the IAM policy on the function's role says
  exactly that.
- **pushimage** — runs `.wayfinder/ensure-image.sh`, which puts this `RELEASE`'s
  image in ECR. It runs with the deployment identity's own credentials, so there
  is no registry credential stored anywhere.
- **api** — the Lambda function.
- **apigateway** — one `ANY /api/{proxy+}` route to the function, which routes
  requests itself. Adding an endpoint is a change to Go and nothing else.
- **web** — CloudFront, serving the web app on `/*` and forwarding `/api/*` to
  the gateway, so the browser talks to one origin.
- **deployweb** — runs `.wayfinder/deploy-web.sh`, which puts this `RELEASE`'s
  web app in the bucket CloudFront serves.
- **smoke** — runs `make smoke` against the public URL: `/api/healthz` reports
  ok, and an order placed comes back when the orders are listed. A deploy that
  leaves the API broken fails here instead of being called a success. It is
  given no cloud credentials, because it only needs the URL.

### The two scripts, and what they refuse to do twice

`ensure-image.sh` and `deploy-web.sh` check before they act, and both are
runnable by hand from the repository root.

`ensure-image.sh` creates the ECR repository — one per service, named after it —
the first time anything deploys, so two pull requests opened at once cannot fail
on a repository that is not there yet; the second create is refused with
`RepositoryAlreadyExistsException`, which means the repository is there, which
is all the deploy needed. It then builds and pushes only when the tag is missing
from ECR. `deploy-web.sh` leaves
a `.release-<RELEASE>` object in the bucket, and skips everything when the
object is already there.

Between them, a machine with no docker and no node can deploy a release someone
else built — which is what lets an agent working in a container deploy this
stack. Set `SOURCE_BUCKET` to another instance's bucket and `deploy-web.sh`
copies that build across rather than making one.

## Deploying it yourself

You need `make`, the `aws` CLI, `curl` and `jq`, plus `docker` if the image for
your `RELEASE` is not in ECR yet and `go` and `node` to build it: `pushimage`,
`deployweb` and `smoke` run on your machine, not in Wayfinder.

```bash
wf up -f Wayfinder.yaml -i ${{ .Inputs.serviceName }}-dev \
  -w <workspace> -e <environment> \
  --identity <workspace>/<environment>/aws-<environment> --region <region> \
  --env-var RELEASE=$(git describe --always --dirty)
```

`RELEASE` tags the image and is compiled into the binary, so `/api/healthz` on
the running function names the commit it came from. It also decides whether
anything gets built: a `RELEASE` already in ECR is deployed as it is, never
rebuilt. `git describe --always --dirty` gives an edited tree a `RELEASE` of its
own, so your change is the thing that deploys. To push over a `RELEASE` that is
already there, export `FORCE_BUILD=1` before you run `wf up`.

`FORCE_BUILD`, `SOURCE_BUCKET` and `EXPECT_ORDER_ID` are read from the
environment `wf` runs in, not passed with `--env-var`; the header of
`Wayfinder.yaml` says what each one does.

Tear it down with `wf down -i ${{ .Inputs.serviceName }}-dev -e <environment>`.

## The delivery pipeline

| When | What happens |
| --- | --- |
| A pull request opens or updates | Tests, a dry run, then a preview instance `${{ .Inputs.serviceName }}-pr<number>` whose URL is commented on the pull request |
| The pull request closes | The preview instance is destroyed |
| A merge to `main` | `${{ .Inputs.serviceName }}-develop` is deployed |
| A `v*` tag is pushed | `${{ .Inputs.serviceName }}-prod` is deployed, behind the `production` GitHub environment |

CI never logs into a registry. `pushimage` creates this service's ECR repository
the first time anything deploys and pushes to it as the deployment identity, so
there is no registry address to record and no registry credential to store. The
deploy jobs run on a plain ubuntu runner rather than in a container, because
`pushimage` and `deployweb` need docker, make, go and node when there is
something to build, which the runner already has.

### What CI needs

Nothing to set up by hand. Wayfinder created one service account per
environment, the `preview`, `develop` and `production` GitHub environments, and
these variables, when it created this repository:

| Variable | Set on | |
| --- | --- | --- |
| `WAYFINDER_SERVER` | The repository | Your Wayfinder API URL |
| `WF_REGION` | The repository | The AWS region to deploy into, e.g. `eu-west-2` |
| `WAYFINDER_SERVICE_ACCOUNT` | Each environment | The account that job signs in as, as `tenant:workspace:name` |
| `WAYFINDER_ENVIRONMENT` | Each environment | The Wayfinder environment that job deploys to |

No credential is stored anywhere. A job declares `environment: preview`,
`develop` or `production`; GitHub mints an OIDC token naming that environment,
and `wf` exchanges it for the account in `WAYFINDER_SERVICE_ACCOUNT`, which
trusts that one subject and no other. So the account a pull request can use
cannot deploy to production, and a job that does not declare its environment
cannot sign in at all. The tenant and workspace come from the reference, so
there is nothing else to tell `wf`.

Two more variables are yours, and neither is required:

| Variable | |
| --- | --- |
| `WF_IDENTITY` | The cloud identity Wayfinder deploys through. Without it, `<workspace>/<environment>/aws-<environment>`, which is what an account vend creates |
| `WF_DNS_ZONE` | Without one the web app answers on CloudFront's own `*.cloudfront.net` domain |

Set either on the repository and every deployment uses it; set it on one
environment as well and that environment uses its own, because an environment's
value wins over the repository's. `WF_REGION` behaves the same way, so
production can deploy to a different region from `develop`.

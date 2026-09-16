# Go Lambda web app template

A team's starting point for a service that runs on AWS Lambda rather than on a
cluster: a Go API in a container behind API Gateway, a DynamoDB table, and a
single-page web app served by CloudFront.

A repository made from this template can deploy the moment it exists. Creating
it also creates one Wayfinder service account per environment, the `preview`,
`develop` and `production` GitHub environments, and the variables the workflows
read — so nobody has to make an account, a federated credential or a role
binding by hand, and a pull request's account cannot reach production.
`skeleton/README.md` lists every variable and where it is set.

It is the same delivery pipeline as the other golden paths — see
[../DELIVERY-PIPELINE.md](../DELIVERY-PIPELINE.md) — so a pull request gets its
own preview of the whole stack, merging deploys `develop`, and a `v*` tag deploys
production.

**Why this one exists beside [`go-app`](../go-app):** `go-app` deploys a Helm
chart and needs a cluster to deploy it to. A team whose cloud account was vended
for them has an account, a deployment identity and somewhere to keep Terraform
state — and no cluster. This template is for that team.

## What a repository from it deploys

Seven components, deployed in this order:

```
db ───────────────┐
                  ▼
pushimage ──────► api ─► apigateway ─► web ─► deployweb ─► smoke
```

`db` is the DynamoDB table, `api` the Lambda function, `apigateway` one
`ANY /api/{proxy+}` route to it, and `web` the CloudFront distribution serving
the single-page app on `/*` and forwarding `/api/*` to the gateway. The other
three run on the machine doing the deploy.

There is no ECR component. The registry is per account and always there, and
the repository is named after the service, so the image address follows from the
account, the region and the name, and nothing has to be deployed before it is
known.

### The three actions

- **pushimage** runs `.wayfinder/ensure-image.sh`. It creates the service's ECR
  repository the first time anything deploys — so two pull requests opened at
  once cannot fail on a repository that is not there yet, the second create
  being refused with `RepositoryAlreadyExistsException` — and builds and pushes
  only when the `RELEASE` tag is missing from ECR.
- **deployweb** runs `.wayfinder/deploy-web.sh`. It leaves a
  `.release-<RELEASE>` object in the SPA bucket and skips everything when that
  object is already there. Set `SOURCE_BUCKET` and it copies another instance's
  build across rather than making one.
- **smoke** runs `make smoke` against the public URL: `/api/healthz` reports ok,
  and an order placed comes back when the orders are listed. It takes only
  `curl` and `jq`, and is given no cloud credentials.

Both scripts check before they act, which is what lets a machine with no docker
and no node deploy a release someone else built — an agent working in a
container, for instance.

Because a `RELEASE` already in ECR is deployed rather than rebuilt, deploy by
hand with `--env-var RELEASE=$(git describe --always --dirty)`, so an edited
tree gets a `RELEASE` of its own. `FORCE_BUILD=1` in the environment pushes over
one that is already there. CI passes the commit sha and is unaffected.

| File | What it is |
| --- | --- |
| `wayfinder-template.yaml` | The template: its inputs, the service accounts and GitHub variables scaffolding creates, and which files are copied raw. |
| `RepoTemplate-go-lambda-webapp.yaml` | Registers the template with Wayfinder. |
| `skeleton/` | Everything written into the generated repository. |

See [../ANATOMY.md](../ANATOMY.md) for which of those two YAML files does what.

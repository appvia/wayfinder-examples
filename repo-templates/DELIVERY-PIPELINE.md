# The delivery pipeline

What the golden-path templates — [`go-app`](./go-app), [`python-app`](./python-app),
[`node-app`](./node-app) — set up in a repository they create, and what you need
in place before it works.

## What the workflows do

| When | What happens |
| --- | --- |
| Pull request opened or pushed to | Tests run, an image is built, the stack is validated with a dry run, then a preview instance `<service>-pr<number>` is deployed and its URL commented on the pull request. |
| Pull request closed | The preview instance is destroyed, merged or not. |
| Merge to `main` | An image tagged `sha-<short sha>` is built and `<service>-develop` is deployed. |
| Tag `v*` pushed | The tagged tree is tested and built as `<image>:<tag>`, and that exact tag is deployed to `<service>-prod`. |

Cutting a release is therefore `git tag v1.0.0 && git push origin v1.0.0`.

Production has two independent gates. GitHub's `environment: production` gate
holds the deploy for your reviewers, and it is not decoration — it scopes the
OIDC token to `repo:<owner>/<repo>:environment:production`, which is exactly what
the production credential trusts. Setting the `WF_PROD_REQUIRE_APPROVAL` variable
to `true` adds Wayfinder's own gate on the infrastructure plan, pausing per
CloudResource until someone runs `wf approve cloudresource`.

## Where configuration lives

Nothing repository-specific is written into the workflow files. They are copied
verbatim from the template so they stay upgradeable, and configuration splits in
two:

- **`.wayfinder/ci.env`**, written by Wayfinder when it creates the repository —
  service name, tenant, environments, service account names, image repository.
  Per-repository values go here.
- **GitHub repository or organisation variables** — everything specific to your
  Wayfinder installation, shared across repositories.

| Variable | What it is |
| --- | --- |
| `WF_SERVER` | Wayfinder API URL. |
| `WF_TOOLBOX_IMAGE` | Image providing the `wf` CLI. Defaults to `quay.io/appvia-wayfinder/wftoolbox:latest`. |
| `WF_WORKSPACE` | Workspace to deploy into, when the template's `workspace` input was left blank. |
| `WF_HOST_CLUSTER` | Cluster to deploy the workload to. |
| `WF_IDENTITY` | Cloud identity Wayfinder provisions cloud resources through. |
| `WF_REGION` | Cloud region. |
| `WF_DNS_ZONE` | DNS zone the service hostname is allocated from. |
| `WF_HOST_CLUSTER_PROD`, `WF_IDENTITY_PROD`, `WF_REGION_PROD`, `WF_DNS_ZONE_PROD` | Production overrides; each falls back to the value above. |
| `WF_PROD_REQUIRE_APPROVAL` | `true` makes production deploys pause for infrastructure plan approval. |
| `WF_PROD_APPROVAL_TIMEOUT` | How long to wait at each approval gate. `0` fails fast instead of waiting. |
| `WF_EXPECT_INSTANCE_ID` | Asserts which Wayfinder installation production is talking to, guarding against a misconfigured `WF_SERVER`. |

`.wayfinder/ci-env.sh` is the glue: each job runs it for its stage, and it
resolves those two sources into the `WAYFINDER_*` variables the CLI reads, plus
the deployment target flags.

## Before CI can deploy

These are the parts people miss.

**1. Three service accounts, one per environment.** Each trusts a different
GitHub OIDC subject, so a credential a pull request can use cannot reach
production. Nothing long-lived is stored in the repository — GitHub mints a
short-lived token per run and Wayfinder exchanges it.

```bash
TENANT=acme WORKSPACE=team-a REPO=acme/payments SERVICE=payments \
  ./setup-ci-service-accounts.sh
```

See [`setup-ci-service-accounts.sh`](./setup-ci-service-accounts.sh) for what it
creates and the imperative equivalents.

**2. Catalogue write for the preview service account.** `Wayfinder.yaml` refers
to its plan as `file:plans/...`, which publishes that plan to the workspace
catalogue — and that happens on the dry run the `validate` job does, not just on
a real deploy. Grant it, or set the `storagePlan` input to a catalogue reference
such as `aws-s3-data-bucket@1.0.0` so nothing needs publishing.

**3. GitHub environments `develop` and `production`.** Create both, with your
reviewers on `production`. Without them GitHub will not mint the
environment-scoped token these credentials trust, and the deploy jobs fail to
authenticate.

**4. The cluster must be able to pull your image.** Packages pushed to GHCR are
private by default. Either make the package public or give the target namespace
an image pull secret. This is the most common cause of a preview that deploys
and then sits in `ImagePullBackOff` until the job times out.

**5. A Gateway, if you want the service reachable.** Pass `gatewayName` (and
`gatewayNamespace`) when scaffolding to publish through an existing Gateway API
Gateway. Left blank, the service deploys and is reachable inside the cluster
only, and the stack publishes no `url` output — the pull request comment says so
rather than showing a link.

## Naming

| Thing | Convention | Example (`payments`, PR 42) |
| --- | --- | --- |
| Preview instance and namespace | `<service>-pr<N>` | `payments-pr42` |
| Develop instance | `<service>-develop` | `payments-develop` |
| Production instance | `<service>-prod` | `payments-prod` |
| Service accounts | `<service>-ci-{preview,develop,prod}` | `payments-ci-prod` |
| Image | `<registry>/<org>/<repo>:<tag>` | `ghcr.io/acme/payments:v1.2.3` |

# ${{ .Inputs.serviceName }}

${{ .Inputs.description }}

Scaffolded by Wayfinder from the `java-app` template.

## What is here

| Path | What it is |
| --- | --- |
| `src/main/java/com/example/app/` | The service. Spring Boot with `/`, `/healthz` and `/readyz`. |
| `src/test/java/` | The JUnit suite. |
| `pom.xml` | Dependencies and build, versions pinned. |
| `Wayfinder.yaml` | The stack: what gets deployed and what it needs. |
| `charts/app/` | The Helm chart for the workload. |
| `plans/` | The CloudResourcePlan backing the storage component. |
| `.wayfinder/ci.env` | This repository's Wayfinder identity, read by every CI job. |
| `.github/workflows/` | Pull request previews, deploy to develop, deploy to production. |

## Running it locally

```bash
make test     # mvn test
make lint     # compiles with -Xlint:all -Werror
make run      # mvn spring-boot:run on port ${{ .Inputs.port }}
```

```bash
curl localhost:${{ .Inputs.port }}/healthz
curl localhost:${{ .Inputs.port }}/
```

## Deploying it

CI deploys this service for you. If you want to deploy a copy of your own from
your laptop, into your own instance:

```bash
wf up -i ${{ .Inputs.serviceName }}-$USER -e ${{ .Inputs.developEnvironment }}
wf down -i ${{ .Inputs.serviceName }}-$USER -e ${{ .Inputs.developEnvironment }}
```

`wf up` needs an image to run, so pass one that already exists:

```bash
wf up -i ${{ .Inputs.serviceName }}-$USER --env-var IMAGE=<registry>/<org>/<repo>:<tag> --env-var RELEASE=local
```

## How CI works

| When | What happens |
| --- | --- |
| Pull request opened or pushed to | Tests run, an image is built, the stack is validated with a dry run, then a preview instance `${{ .Inputs.serviceName }}-pr<number>` is deployed in `${{ .Inputs.previewEnvironment }}` and its URL is commented on the pull request. |
| Pull request closed | The preview instance is destroyed, merged or not. |
| Merge to `main` | Tests run, an image tagged `sha-<short sha>` is built, and `${{ .Inputs.serviceName }}-develop` is deployed in `${{ .Inputs.developEnvironment }}`. |
| Tag `v*` pushed | The tagged tree is tested and built as `<image>:<tag>`, and that exact tag is deployed to `${{ .Inputs.serviceName }}-prod` in `${{ .Inputs.prodEnvironment }}`. |

Cutting a release is therefore:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

### Where CI configuration lives

Nothing repository-specific is written into the workflow files — they are
copied verbatim from the template so they stay upgradeable. Configuration
splits in two:

- **`.wayfinder/ci.env`** — values specific to this repository: the service
  name, tenant, environments, service accounts and image repository. Add new
  per-repository values here.
- **GitHub repository or organisation variables** — values specific to your
  Wayfinder installation, shared by every repository:

  | Variable | What it is |
  | --- | --- |
  | `WF_SERVER` | Wayfinder API URL. |
  | `WF_TOOLBOX_IMAGE` | Image providing the `wf` CLI. Defaults to `quay.io/appvia-wayfinder/wftoolbox:latest`. |
  | `WF_WORKSPACE` | Workspace to deploy into, when `.wayfinder/ci.env` leaves it blank. |
  | `WF_HOST_CLUSTER` | Cluster to deploy the workload to. |
  | `WF_IDENTITY` | Cloud identity Wayfinder provisions cloud resources through. |
  | `WF_REGION` | Cloud region. |
  | `WF_DNS_ZONE` | DNS zone the service hostname is allocated from. |
  | `WF_HOST_CLUSTER_PROD`, `WF_IDENTITY_PROD`, `WF_REGION_PROD`, `WF_DNS_ZONE_PROD` | Production overrides. Each falls back to the value above. |
  | `WF_PROD_REQUIRE_APPROVAL` | Set to `true` to make production deploys pause for plan approval. |
  | `WF_EXPECT_INSTANCE_ID` | Guards production against a misconfigured `WF_SERVER`. |

### What CI needs before it can run

Three Wayfinder service accounts, one per environment, each trusting a
different GitHub OIDC subject so a preview credential cannot reach production:

```bash
wf create serviceaccount ${{ .Inputs.serviceName }}-ci-preview -w <workspace>
wf create serviceaccountcredential github-preview \
  --service-account <tenant>:<workspace>:${{ .Inputs.serviceName }}-ci-preview \
  --github-repo ${{ .Repo.Organization }}/${{ .Repo.Name }} --github-pull-request

wf create serviceaccount ${{ .Inputs.serviceName }}-ci-develop -w <workspace>
wf create serviceaccountcredential github-develop \
  --service-account <tenant>:<workspace>:${{ .Inputs.serviceName }}-ci-develop \
  --github-repo ${{ .Repo.Organization }}/${{ .Repo.Name }} --github-environment develop

wf create serviceaccount ${{ .Inputs.serviceName }}-ci-prod -w <workspace>
wf create serviceaccountcredential github-prod \
  --service-account <tenant>:<workspace>:${{ .Inputs.serviceName }}-ci-prod \
  --github-repo ${{ .Repo.Organization }}/${{ .Repo.Name }} --github-environment production
```

Each needs the `deployer` role in its environment. The preview account also
needs catalogue write, because `Wayfinder.yaml` refers to its plan as
`file:plans/...`, which publishes that plan to the workspace catalogue — and
that happens on a dry run too.

You also need GitHub environments named `develop` and `production` (put your
reviewers on `production`), and the cluster must be able to pull your image.
Packages published to GHCR are private by default, so either make the package
public or give the cluster an image pull secret.

## Storage

`Wayfinder.yaml` provisions an S3 bucket per stack instance and grants this
service read-write access to it. The bucket name arrives as `BUCKET_NAME`.
There are no credentials anywhere in this repository: the pod authenticates
with its own cloud identity, which Wayfinder attaches to its Kubernetes service
account.

To drop the bucket, remove the `storage` component from `Wayfinder.yaml` along
with the `workloadIdentity` block and the `env` entries that reference it.

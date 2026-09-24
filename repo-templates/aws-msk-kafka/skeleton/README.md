# ${{ .Stack.Name }}

${{ .Inputs.description }}

One Amazon MSK Serverless Kafka cluster per environment, in that environment's
platform VPC. Applications sign in with IAM — there is no Kafka password
anywhere — and find the cluster by its tags, so no application stack is given
its ARN or its brokers.

| Environment | Instance | Cluster | Deployed when |
| --- | --- | --- | --- |
| `${{ .Inputs.developEnvironment }}` | `${{ .Stack.Name }}-develop` | `${{ .Inputs.kafkaCluster }}-${{ .Inputs.developEnvironment }}` | a pull request merges to `main` |
| `${{ .Inputs.prodEnvironment }}` | `${{ .Stack.Name }}-prod` | `${{ .Inputs.kafkaCluster }}-${{ .Inputs.prodEnvironment }}` | a `v*` tag is pushed |

A pull request deploys nothing. It lints the files and runs
`wf deploy --dry-run` against `${{ .Inputs.previewEnvironment }}`. A cluster per
pull request would carry the same tags as the cluster already in that
environment, and every application there would fail to find either of them
until it was gone.

## What it costs

MSK Serverless bills while the cluster exists, whether anything uses it or not:
about **$0.75 an hour per cluster** (roughly $550 a month), plus **$0.0015 an
hour per partition**, plus storage and data in and out. Those are us-east-1 list
prices; the [MSK pricing page](https://aws.amazon.com/msk/pricing/) has your
region's. Two environments is two clusters.

## Layout

| | |
| --- | --- |
| `Wayfinder.yaml` | The stack: find the VPC, create the cluster, check it answers |
| `plans/aws-vpc-discovery.yaml` | Finds the VPC tagged `Environment=<environment>` and its private subnets. Creates nothing |
| `plans/aws-msk-serverless.yaml` | The cluster, its security group, and the `produce-consume` policy applications are granted |
| `Makefile` | `make lint`: every YAML file parses, and every plan `Wayfinder.yaml` names exists |
| `.wayfinder/ci.env` | The stack name and the `wf` build CI installs |
| `.github/workflows/` | Lint and dry run on a pull request, `develop` on merge, production on a tag |

## The stack

```
network ─► kafka ─► smoke
```

- **network** — finds the VPC tagged `Environment=<environment>` and its
  subnets tagged `Tier=private`. The deploy fails
  unless exactly one such VPC is in the account and region.
- **kafka** — the MSK Serverless cluster in those private subnets, named
  `${{ .Inputs.kafkaCluster }}-<environment>`. Its security group admits TCP 9098
  from the whole VPC CIDR; IAM authentication decides who connects.
- **smoke** — fails the deploy if the cluster published no IAM bootstrap
  brokers, and prints the brokers and the tags an application finds it by. It
  is given no cloud credentials.

Deploying a second instance of this stack into the same environment fails on
the security group name, which is taken, instead of creating a second cluster
with the same tags.

## How an application finds it

The cluster is tagged:

| Tag | Value |
| --- | --- |
| `Environment` | the Wayfinder environment name, e.g. `${{ .Inputs.developEnvironment }}` |
| `KafkaCluster` | `${{ .Inputs.kafkaCluster }}` |
| `ManagedBy` | `Wayfinder` |

An application stack in the same AWS account and region deploys the
`aws-msk-discovery` plan, which looks the cluster up by those tags and returns
`bootstrap_brokers`, `cluster_arn` and `cluster_name`. Granting the
application's workload identity `produce-consume` on that component gives it
permission to connect, create topics, produce, consume and use consumer groups:

```yaml
components:
  kafka:
    type: CloudResource
    plan: file:plans/aws-msk-discovery.yaml
    inputs:
      - name: cluster
        value: ${{ .Inputs.kafkaCluster }}   # leave out for "default"

  app:
    workloadIdentity:
      access:
        - to: kafka
          consumptionPolicy: produce-consume
```

Clients connect to `bootstrap_brokers` with SASL/IAM over TLS on port 9098.

## Before the first deploy

- **A VPC tagged for each environment**, in the account and region each
  environment deploys to: `Environment=<environment>` and `ManagedBy=Wayfinder`
  on the VPC, `Tier=private` on at least two subnets in different availability
  zones. A VPC you already have works once it and its subnets carry these tags.
- **A cloud identity per environment** named
  `<workspace>/<environment>/aws-<environment>`, or set the `WF_IDENTITY`
  variable on the GitHub environment to the one to use. It must be able to
  create MSK clusters and security groups.
- **Reviewers on the `production` GitHub environment**, if a tag should wait
  for someone before it deploys. Set `WF_PROD_REQUIRE_APPROVAL=true` to also
  pause on Wayfinder's own approval of the infrastructure plan.

## GitHub variables

Wayfinder set these when it created this repository.

| Variable | Where | What it is |
| --- | --- | --- |
| `WAYFINDER_SERVER` | repository | Wayfinder API URL |
| `WF_REGION` | repository | AWS region, `${{ .Inputs.region }}` |
| `WAYFINDER_SERVICE_ACCOUNT` | each of `preview`, `develop`, `production` | The service account that environment's jobs sign in as |
| `WAYFINDER_ENVIRONMENT` | each of `preview`, `develop`, `production` | The Wayfinder environment those jobs deploy to |

Set these yourself if you need them:

| Variable | What it does |
| --- | --- |
| `WF_IDENTITY` | Cloud identity to deploy through, instead of `<workspace>/<environment>/aws-<environment>` |
| `WF_PROD_REQUIRE_APPROVAL` | `true` pauses a production deploy until the plan is approved with `wf approve cloudresource` |
| `WF_PROD_APPROVAL_TIMEOUT` | How long to wait at each approval gate |
| `WF_EXPECT_INSTANCE_ID` | Fails a production deploy that is talking to a different Wayfinder installation |

## Deploying it yourself

```bash
wf deploy -f Wayfinder.yaml -i ${{ .Stack.Name }}-develop \
  -w <workspace> -e ${{ .Inputs.developEnvironment }} \
  --identity <workspace>/${{ .Inputs.developEnvironment }}/aws-${{ .Inputs.developEnvironment }} \
  --region ${{ .Inputs.region }}
```

Removing it is `wf down -i ${{ .Stack.Name }}-develop -w <workspace> -e ${{ .Inputs.developEnvironment }}`.
Every application using the cluster loses it, and its next deploy fails to find
one.

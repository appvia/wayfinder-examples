# AWS MSK Serverless Kafka template

A team vends a repository from this template and gets one Amazon MSK Serverless
Kafka cluster in each environment's platform VPC. Application stacks in the
same AWS account and region find it by tag with the `aws-msk-discovery` plan, so
none of them is given its ARN or its brokers, and none needs a Kafka password:
clients sign in with IAM.

Creating the repository also creates one Wayfinder service account per
environment, the `preview`, `develop` and `production` GitHub environments, and
the variables the workflows read. Nobody makes an account, a federated
credential or a role binding by hand, and a pull request's account cannot reach
production. `skeleton/README.md` lists every variable and where it is set.

| When | What happens |
| --- | --- |
| Pull request opened or pushed to | `make lint`, then `wf deploy --dry-run` in `previewEnvironment`. Nothing is created |
| Merge to `main` | `<stack>-develop` deploys the cluster `<kafkaCluster>-<developEnvironment>` |
| Tag `v*` pushed | `<stack>-prod` deploys the cluster `<kafkaCluster>-<prodEnvironment>` |

## Why a pull request deploys nothing

The golden-path services deploy a preview per pull request. This template does
not, and its workflows are its own rather than the copies in
[`_shared/workflows/`](../_shared/workflows), which build a container image and
deploy one. A preview cluster would carry the same `Environment` and
`KafkaCluster` tags as the cluster already in that environment, and every
application there would fail discovery with "Expected exactly one MSK cluster"
until the pull request closed — while billing for a second cluster.

## What it costs

MSK Serverless bills while a cluster exists, used or not: about **$0.75 an
hour per cluster** (roughly $550 a month), plus **$0.0015 an hour per
partition**, plus storage and data in and out. Those are us-east-1 list prices;
the [MSK pricing page](https://aws.amazon.com/msk/pricing/) has other regions'.
The default inputs deploy two clusters, `develop` and `prod`.

## The tag contract

| Tag | Value |
| --- | --- |
| `Environment` | The Wayfinder environment name, e.g. `dev` |
| `KafkaCluster` | The `kafkaCluster` input, `default` unless changed |
| `ManagedBy` | `Wayfinder` |

The security group admits TCP 9098 from the whole VPC CIDR. IAM authentication
decides who connects: a workload identity granted the `produce-consume`
consumption policy can connect, create topics, produce, consume and use
consumer groups, and nothing else can.

## How an application uses it

An application stack deploys the `aws-msk-discovery` plan as a component —
given `cluster` only when it wants a cluster other than `default` — and grants
its workload identity `produce-consume` on that component. The component's
`bootstrap_brokers` output is what the application's Kafka client connects to.
The application templates carry that plan; this one does not.

## Before anyone vends it

- **A VPC per environment**, tagged `Environment=<environment>` and
  `ManagedBy=Wayfinder`, with at least two subnets tagged `Tier=private` in
  different availability zones. The `onboard-aws` workflow in the Wayfinder
  repository, `examples/workflows/onboard-aws`, creates one when run with its
  `createVpc` input. The deploy fails unless exactly one VPC in the account and
  region carries the environment's tag.
- **A cloud identity per environment**, `<workspace>/<environment>/aws-<environment>`
  unless the repository's `WF_IDENTITY` variable names another, able to create
  MSK clusters and EC2 security groups.

## Inputs

| Input | Default | What it sets |
| --- | --- | --- |
| `kafkaCluster` | `default` | The `KafkaCluster` tag and the first half of the cluster name |
| `description` | empty | The README and the stack's description |
| `previewEnvironment` | `dev` | Where a pull request's dry run validates |
| `developEnvironment` | `dev` | Where a merge deploys |
| `prodEnvironment` | `prod` | Where a `v*` tag deploys |
| `region` | `eu-west-2` | The AWS region, which must hold each environment's VPC |

There is no name input. Stack instances are named after the stack the
repository is created for, `<stack>-develop` and `<stack>-prod`.

```bash
wf apply -f repo-templates/aws-msk-kafka/RepoTemplate-aws-msk-kafka.yaml
wf create stack kafka --from-template aws-msk-kafka --dry-run
```

## Checking it

```bash
python3 repo-templates/hack/check-templates.py
wf apply -w <workspace> --dry-run server -f repo-templates/aws-msk-kafka/skeleton/plans/aws-msk-serverless.yaml
```

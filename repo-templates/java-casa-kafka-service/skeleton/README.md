# ${{ .Inputs.serviceName }}

${{ .Inputs.description }}

One web page that sends a message through Kafka and shows when it has come
back. A person types a message and presses Send; the page shows the message's
reference and its status, `SUBMITTED` until message-service has read it back off
the Kafka topic and `RECEIVED` after. The page reloads itself until it says
`RECEIVED`.

It is the smallest app that uses every part of this stack: a GOV.UK CASA
frontend on Node 22, a Java 25 Spring Boot 4 API, Avro records on Kafka with
Spring Cloud Stream, Amazon MSK with IAM sign-in, and ECS Fargate behind
CloudFront. Replace the message with your own domain and the rest stays.

## How it fits together

```
  browser
     │ HTTPS
     ▼
  CloudFront (no caching) ◄── POST /api/messages ── the frontend calls the API here
     │ HTTP                                            ▲
     ▼                                                 │
  ALB :80 ──── /* ──────► frontend          GOV.UK CASA, Node 22, ECS Fargate :3000
     │
     └────── /api/* ────► message-service   Java 25, Spring Boot, ECS Fargate :8080
                             │     ▲
                     produce │     │ consume (marks the message RECEIVED)
                             ▼     │
                  MSK topic <stack>.<instance>.message-submitted
                  MessageSubmitted records, Avro binary, IAM sign-in
```

- **frontend** is a [CASA](https://github.com/dwp/govuk-casa) app with one page:
  a GOV.UK form with one field. It holds no data of its own; it sends the
  message to message-service and reads its status back.
- **message-service** validates the message, gives it a reference, and
  publishes a `MessageSubmitted` record. The record is checked against its Avro
  schema and written as Avro binary, so a record that does not match the schema
  is refused before it reaches the topic. The same service consumes the topic
  and moves the message from `SUBMITTED` to `RECEIVED`. Messages are held in
  memory, so a restart forgets them.
- **Kafka** is the environment's MSK cluster. Clients sign in with IAM, so there
  is no Kafka password anywhere. message-service is granted `produce-consume` on
  it in `Wayfinder.yaml`, and Wayfinder writes that grant into its task role.
  The frontend is granted nothing and cannot reach Kafka.

The site answers on HTTPS only, on CloudFront's `*.cloudfront.net` name. The
load balancer accepts connections from CloudFront's addresses and nothing else,
so nobody can skip HTTPS by calling it directly. To use a domain of your own,
set `hosted_zone_name` and `hostname` on the `web` component in
`Wayfinder.yaml`; Wayfinder then creates the certificate in us-east-1 and the
Route53 record.

## Before you deploy

This stack builds no VPC and no Kafka cluster. It finds the ones the platform
team built for the environment you deploy to, by tag, in the AWS account and
region you deploy into. The first deploy fails on `network` or `kafka` until
both exist:

| Resource | Tags it must carry | Found by |
| --- | --- | --- |
| VPC, exactly one per account and region for the environment | `Environment=<environment>`, `ManagedBy=Wayfinder` | `plans/aws-vpc-discovery.yaml` |
| Its subnets | `Environment=<environment>`, `Tier=public` or `Tier=private` | `plans/aws-vpc-discovery.yaml` |
| MSK cluster | `Environment=<environment>`, `KafkaCluster=${{ .Inputs.kafkaCluster }}` | `plans/aws-msk-discovery.yaml` |

`<environment>` is the Wayfinder environment name, such as `dev`.

- **The VPC**: the `onboard-aws` workflow in the Wayfinder repository's
  `examples/workflows/onboard-aws` creates one tagged this way when it is run
  with `createVpc`. A VPC you already have works once it and its subnets carry
  these tags.
- **The MSK cluster**: deploy the `aws-msk-kafka` template from
  [appvia/wayfinder-examples](https://github.com/appvia/wayfinder-examples) into
  the same environment. Its security group admits Kafka's IAM port, 9098, from
  the whole VPC, so this stack adds no security group rule of its own.

Both services run in the **public** subnets with a public IP, so they can pull
their images from ECR without a NAT gateway; only the load balancer's security
group can reach them. If your VPC has a NAT gateway, change
`public_subnet_ids` to `private_subnet_ids` on the `api` and `frontend`
components in `Wayfinder.yaml` and give both `assign_public_ip: false`.

## Layout

| | |
| --- | --- |
| `message-service/` | The Java API: Maven project, Avro schema, Spring Cloud Contract, its own `Dockerfile` |
| `frontend/` | The CASA page: Node project, Nunjucks views, its own `Dockerfile` |
| `Wayfinder.yaml` | The stack: the discovered VPC and Kafka cluster, the load balancer, both images, both services, and a check that they work together |
| `plans/` | The CloudResourcePlans the stack deploys. Yours to edit |
| `docker-compose.yaml` | Kafka, both apps and a proxy standing in for the load balancer, for `make up` |
| `Makefile` | Every build and check CI and the stack run |
| `.wayfinder/` | `ci.env` (the service name and the `wf` build CI installs), the script CI uses to install `wf`, and the one `pushimages` runs |
| `.github/workflows/` | A preview per pull request, `develop` on merge, production on a tag |

## Working on it

You need a JDK 25, Maven, Node 22 and docker.

```bash
make test     # both apps' unit tests, and the contract test
make lint     # compiles message-service with every warning an error; runs the frontend's linter
make up       # Kafka, both apps and a proxy on http://localhost:8000
make smoke URL=http://localhost:8000
make down
```

`make up` runs one plain-text Kafka broker in place of MSK, and nginx on port
8000 in place of the load balancer, sending `/api/*` to message-service and
everything else to the frontend. Open http://localhost:8000 to use the page.
That is also what lets `make smoke` check your laptop with the same requests it
sends a deployed instance. Kafka is published on `localhost:9094` if you want
to read the topic with your own client.

`make smoke` checks, through one URL:

1. the frontend answers `/healthz` and `/` renders the page;
2. message-service answers `/api/healthz` with `ok` and names its release;
3. a message POSTed to `/api/messages` is accepted with a reference;
4. that message reads back as `RECEIVED` within about 90 seconds.

A message stuck at `SUBMITTED` means its record never made it through Kafka.

## The stack

Eight components, deployed in this order:

```
network ───► platform ───► web ─────────────┐
kafka ──────────────────┐                   ▼
pushimages ─────────────┴──► api ───► frontend ───► smoke
```

- **network** finds the environment's VPC and its subnets. Creates nothing.
- **kafka** finds the environment's MSK cluster and its IAM bootstrap brokers.
  Creates nothing.
- **platform** creates the ECS cluster, the load balancer and the security
  groups in that VPC. The load balancer answers 404 for any path no service
  has claimed.
- **web** creates the CloudFront distribution in front of the load balancer.
- **pushimages** runs `.wayfinder/ensure-image.sh` once per app, which puts
  this `RELEASE`'s image in ECR. It creates the app's ECR repository the first
  time anything deploys, and builds and pushes only when the tag is missing. It
  runs with the deployment identity's own credentials, so there is no registry
  credential stored anywhere.
- **api** runs message-service on ECS, serving `/api/*`.
- **frontend** runs the CASA page on ECS, serving everything else.
- **smoke** runs `make smoke` against the CloudFront URL. A deploy where the
  services start but a message never reaches `RECEIVED` fails here instead of
  being called a success. It is given no cloud credentials, because it only
  needs the URL.

## Deploying it yourself

You need `make`, the `aws` CLI, `curl` and `jq`, plus `docker` if the images
for your `RELEASE` are not in ECR yet. Both apps build inside their
Dockerfiles, so no JDK or Node is needed for a deploy: `pushimages` and `smoke`
run on your machine, not in Wayfinder.

```bash
wf up -f Wayfinder.yaml -i ${{ .Inputs.serviceName }}-dev \
  -w <workspace> -e <environment> \
  --identity <workspace>/<environment>/aws-<environment> --region <region> \
  --env-var RELEASE=$(git describe --always --dirty)
```

`RELEASE` tags both images, and message-service reports it on `/api/healthz`,
so a running instance names the commit it came from. It also decides whether
anything gets built: a `RELEASE` already in ECR is deployed as it is, never
rebuilt. `git describe --always --dirty` gives an edited tree a `RELEASE` of
its own, so your change is the thing that deploys. To push over a `RELEASE`
that is already there, export `FORCE_BUILD=1` before you run `wf up`.

The first deploy of an instance takes 20 to 30 minutes, most of it creating the
CloudFront distribution.

### What it costs

Each instance runs a load balancer, two Fargate tasks (0.5 vCPU and 1 GB for
message-service, 0.25 vCPU and 0.5 GB for the frontend), four public IPv4
addresses and a CloudFront distribution: about $0.09 an hour, or $65 a month,
in `eu-west-2` before traffic. The VPC and the MSK cluster belong to the
environment and are billed whether or not this stack is deployed. Tear an
instance down when you are finished with it:

```bash
wf down -i ${{ .Inputs.serviceName }}-dev -e <environment>
```

That removes everything this stack created and leaves the VPC and the Kafka
cluster as they were. The instance's topic stays in the cluster.

## The delivery pipeline

| When | What happens |
| --- | --- |
| A pull request opens or updates | Tests, a dry run, then a preview instance `${{ .Inputs.serviceName }}-pr<number>` whose URL is commented on the pull request |
| The pull request closes | The preview instance is destroyed |
| A merge to `main` | `${{ .Inputs.serviceName }}-develop` is deployed |
| A `v*` tag is pushed | `${{ .Inputs.serviceName }}-prod` is deployed, behind the `production` GitHub environment |

Every preview shares the environment's VPC and Kafka cluster, but each
instance has a topic and a consumer group of its own, named after the stack and
the instance, such as `${{ .Stack.Name }}.${{ .Inputs.serviceName }}-pr12.message-submitted`. So one
preview never consumes another's messages. `wf down` leaves an instance's topic
in the cluster; delete it with your Kafka tooling if you need the space back.

CI never logs into a registry. `pushimages` creates this service's two ECR
repositories the first time anything deploys and pushes to them as the
deployment identity. The deploy jobs run on a plain ubuntu runner rather than in
a container, because `pushimages` needs docker and make when there is something
to build, and the runner already has them.

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
cannot sign in at all.

These variables are yours, and none is required:

| Variable | |
| --- | --- |
| `WF_IDENTITY` | The cloud identity Wayfinder deploys through. Without it, `<workspace>/<environment>/aws-<environment>`, which is what an account vend creates |
| `WF_PROD_REQUIRE_APPROVAL` | `true` pauses each production CloudResource for `wf approve cloudresource` |
| `WF_PROD_APPROVAL_TIMEOUT` | How long a production deploy waits at each approval |
| `WF_EXPECT_INSTANCE_ID` | Refuses a production deploy unless `WAYFINDER_SERVER` is the Wayfinder installation with this instance identifier |

Set `WF_IDENTITY` on the repository and every deployment uses it; set it on one
environment as well and that environment uses its own, because an
environment's value wins over the repository's. `WF_REGION` behaves the same
way, so production can deploy to a different region from `develop`.

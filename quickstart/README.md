# Wayfinder quick start — hello-wayfinder

A two-component example you can deploy to a brand-new Wayfinder tenant in minutes:

- an **S3 bucket** (`aws-s3-data-bucket`)
- an **AWS Lambda** whose Python is supplied inline — no container build, no ECR (`aws-lambda-inline`)

The function reads and writes the bucket. The only thing connecting them is **one
access grant** in `Wayfinder.yaml`:

```yaml
workloadIdentity:
  access:
    - consumptionPolicy: read-write
      to: datastorage
```

Wayfinder turns that into the IAM role and policy for you. You write no credentials
anywhere — that is the point.

> **No Kubernetes required.** This uses *cloud-native workload identity*: the Lambda
> gets a real AWS IAM role directly. No cluster, no agent.

## Layout

```
quickstart/
├── Wayfinder.yaml      ← the app: a function + a bucket + one grant
└── plans/
    ├── aws-s3-data-bucket.yaml   ← the bucket (a provider of access)
    └── aws-lambda-inline.yaml    ← the inline-code function (a consumer)
```

## Prerequisites

- `wf` CLI, logged in (`wf login` — run `wf login --refresh` if a fresh tenant returns "Request denied")
- `git`, `curl`, `jq`

## Deploy

> Use the **same region** everywhere below, and make sure your AWS CLI default region
> matches it (`export AWS_REGION=<region>` if unsure) — otherwise the state bucket fails
> with `IllegalLocationConstraintException`. `YOUR-STATE-BUCKET` must be globally unique
> across all of AWS.

```bash
# 1. Connect your cloud — scoped, with provisioning permissions AND a state store.
#    AdministratorAccess is demo-only; bind a scoped policy set for anything real.
#    --apply prompts you to confirm before writing to your AWS account.
wf setup cloudaccess --cloud aws \
  --cloud-access aws-quickstart \
  --aws-account 123456789012 --region REGION \
  --all-workspaces \
  --role-binding arn:aws:iam::aws:policy/AdministratorAccess \
  --state-store --state-store-bucket YOUR-STATE-BUCKET \
  --apply

# 2. Register the plans with your tenant (once per tenant, or after a version bump)
wf apply -f ./plans/

# 3. Create a workspace and environment (a fresh tenant has none).
#    The --key (qstr) is the workspace's resource name — use it, not the display name.
#    create environment does NOT select it, so 'wf use env' is required.
wf create workspace quickstart --key qstr
wf use workspace qstr
wf create environment dev -w qstr
wf use env dev

# 4. Deploy — a new instance needs the cloud target (match step 1's region).
#    wf up runs to completion, streaming provisioning live.
wf up -f ./Wayfinder.yaml -i hello \
  --cloud-access aws-quickstart \
  --region REGION
```

## Prove the wiring

Find the function URL in the deployed outputs, then call it:

```bash
# Lists component outputs — look for the processor's `function_url`
wf outputs hello

# Call the function (paste the function_url from above)
curl -s "<function_url>" | jq
```

You should see the object it just wrote, the current bucket listing, and a note that
the function holds no stored credentials. The Lambda touched the bucket using only the
identity Wayfinder provisioned from that one grant.

## Make it yours

Want a queue, a database table, or a different runtime instead? Ask the Wayfinder
**CloudResourcePlan builder agent** to scaffold a plan, or edit `aws-lambda-inline.yaml`
— change `handler_code` to your own Python and redeploy.

## Tear down

```bash
# Remove the deployment (function, bucket, and the IAM created for them)
wf down -i hello

# Optional: remove the cloud-side setup from step 1 (IAM role, OIDC provider,
# state bucket) and delete the CloudAccess — re-run step 1 with --remove.
wf setup cloudaccess --cloud aws \
  --cloud-access aws-quickstart \
  --aws-account 123456789012 --region REGION \
  --remove
```

## Note on the inline-code function

`aws-lambda-inline` zips a string of Python at apply time. That keeps the quick start
build-free, but it's a getting-started shortcut, not a production pattern — for real
workloads you deploy a container image (see the `aws-lambda-go-handler` style plans).
boto3 is preinstalled in the Lambda Python runtime, so the inline handler needs no
dependencies.

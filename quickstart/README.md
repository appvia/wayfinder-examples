# Wayfinder quick start — hello-wayfinder

A small example you can deploy to a brand-new Wayfinder tenant in minutes:

- an **object store** — an S3 bucket (AWS) or a Storage Account blob container (Azure)
- a **workload** — a no-build AWS Lambda, or (on Azure) a small container running a public,
  open-source image. Either way there's nothing for you to build

The workload reads and writes the store. The only thing connecting them is **one access
grant** in `Wayfinder.yaml`:

```yaml
workloadIdentity:
  access:
    - consumptionPolicy: read-write
      to: datastorage
```

Wayfinder turns that into the workload's cloud identity and the permissions attached to it
— an IAM role and policy on AWS, a managed identity and role assignment on Azure. You write
no credentials anywhere — that is the point.

> **No Kubernetes required.** This uses *cloud-native workload identity*: the workload gets a
> real cloud identity directly. No cluster, no agent.

## Layout

Each cloud is a self-contained folder — pick one:

```
quickstart/
├── aws/
│   ├── Wayfinder.yaml                   ← the app: a function + a bucket + one grant
│   └── plans/
│       ├── aws-s3-data-bucket.yaml      ← the bucket (a provider of access)
│       └── aws-lambda-inline.yaml       ← the inline-code function (a consumer)
└── azure/
    ├── Wayfinder.yaml                   ← the app: a container + a store + one grant
    ├── image/                           ← source for the public image the container runs
    └── plans/
        ├── azure-storage-data.yaml      ← the Storage Account + container (a provider)
        └── azure-container-aci.yaml     ← the container workload on ACI (a consumer)
```

> **AWS ships code inline; Azure ships an image.** AWS Lambda takes its Python inline in the
> plan. Azure Functions on the serverless (Flex Consumption) plan can't be deployed by
> Terraform at all, so the Azure app instead runs a small, **public, open-source image** on
> Container Instances — no code push, no build step, no registry credentials. The image
> source is in `azure/image/` so you can see exactly what runs. (An alternative that deploys
> Python straight to an Azure Function, using a `deploycode` action, lives in
> `azure-localaction/`.)

## Prerequisites

- `wf` CLI, logged in (`wf login` — run `wf login --refresh` if a fresh tenant returns "Request denied")
- Cloud credentials so `--apply` can create the connection: AWS (`aws configure`) or Azure (`az login`)
- `git`, `curl`, `jq`

## Deploy

The flow is identical on both clouds. Pick your cloud's folder, then:

### AWS

> Use the **same region** everywhere below, and make sure your AWS CLI default region
> matches it (`export AWS_REGION=<region>` if unsure) — otherwise the state bucket fails
> with `IllegalLocationConstraintException`. `YOUR-STATE-BUCKET` must be globally unique
> across all of AWS.

```bash
cd aws

# 1. Connect your cloud — scoped, with provisioning permissions AND a state store.
#    AdministratorAccess is demo-only; bind a scoped policy set for anything real.
wf setup cloudaccess --cloud aws \
  --cloud-access aws-quickstart \
  --aws-account 123456789012 --region REGION \
  --all-workspaces \
  --role-binding arn:aws:iam::aws:policy/AdministratorAccess \
  --state-store --state-store-bucket YOUR-STATE-BUCKET \
  --apply

# 2. Register the plans (once per tenant, or after a version bump)
wf apply -f ./plans/

# 3. Create a workspace and environment (a fresh tenant has none).
wf create workspace quickstart --key qstr
wf use workspace qstr
wf create environment dev -w qstr
wf use env dev

# 4. Deploy — a new instance needs the cloud target (match step 1's region).
wf up -f ./Wayfinder.yaml -i hello \
  --cloud-access aws-quickstart \
  --region REGION
```

### Azure

> Run `az login` first. `--state-store-storage-account` must be globally unique across all
> of Azure and **3–24 lowercase letters/numbers only**. Use the **same region** everywhere.

```bash
cd azure

# 1. Connect your cloud — scoped, with provisioning permissions AND a state store.
#    Owner is demo-only; for anything real bind Contributor + User Access Administrator.
wf setup cloudaccess --cloud azure \
  --cloud-access azure-quickstart \
  --azure-subscription 00000000-0000-0000-0000-000000000000 --region uksouth \
  --all-workspaces \
  --role-binding Owner \
  --state-store --state-store-storage-account YOURSTATEACCOUNT --state-store-bucket tfstate \
  --apply

# 2. Register the plans (once per tenant, or after a version bump)
wf apply -f ./plans/

# 3. Create a workspace and environment (a fresh tenant has none).
wf create workspace quickstart --key qstr
wf use workspace qstr
wf create environment dev -w qstr
wf use env dev

# 4. Deploy — a new instance needs the cloud target (match step 1's region).
wf up -f ./Wayfinder.yaml -i hello \
  --cloud-access azure-quickstart \
  --region uksouth
```

## Prove the wiring

Find the workload's URL in the deployed outputs, then call it:

```bash
# Lists component outputs — look for the processor's URL
# (`function_url` on AWS, `app_url` on Azure)
wf outputs hello

# Call it (paste the URL from above)
curl -s "<url>" | jq
```

You should see the object it just wrote, the current store listing, and a note that the
workload holds no stored credentials. It touched the store using only the identity Wayfinder
provisioned from that one grant.

> **Azure:** the container has to start and pull its image on first deploy, so give it a few
> seconds before the first `curl` returns a result.

## Make it yours

Want a queue, a database table, or a different runtime instead? Ask the Wayfinder
**CloudResourcePlan builder agent** to scaffold a plan. On AWS, edit the inline-function plan
— change `handler_code` to your own code and redeploy. On Azure, point the `image` input at
your own published image (the quick-start image's source is in `azure/image/`).

## Tear down

```bash
# Remove the deployment (function, store, and the identity created for them)
wf down -i hello

# Optional: remove the cloud-side setup from step 1 — re-run step 1 with --remove.
# AWS:
wf setup cloudaccess --cloud aws --cloud-access aws-quickstart \
  --aws-account 123456789012 --region REGION --remove
# Azure:
wf setup cloudaccess --cloud azure --cloud-access azure-quickstart \
  --azure-subscription 00000000-0000-0000-0000-000000000000 --region uksouth --remove
```

## Note on getting your code running

Neither cloud asks you to build anything to run this quick start. That keeps it fast, but
each takes a getting-started shortcut — for real workloads you'd build and ship your own
container image.

- **AWS** (`aws-lambda-inline`): the handler's Python is supplied inline in the plan and zipped
  at apply time. boto3 is preinstalled in the Lambda runtime, so it needs no dependencies.
- **Azure** (`azure-container-aci`): Terraform provisions a Container Instance that runs a small,
  **public, open-source image** (`ghcr.io/appvia/wayfinder-examples/azure-blob-hello`). There's
  no code push and no build on your side — Azure pulls the prebuilt image. The image's source
  is in `azure/image/`, and you can point the plan's `image` input at your own.

  > Why a container and not an Azure Function? Terraform can't deploy code to a serverless
  > (Flex Consumption) Function, and the classic code-push path needs App Service compute quota
  > that many new/trial subscriptions have set to zero. A container sidesteps both, and keeps
  > the Azure app a clean mirror of the AWS one. (If you specifically want the Function path,
  > see `azure-localaction/`.)

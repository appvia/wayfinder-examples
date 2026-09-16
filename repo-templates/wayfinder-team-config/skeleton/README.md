# ${{ .Inputs.team }} — Wayfinder platform configuration

This repository is the **source of truth** for the `${{ .Inputs.team }}` team's Wayfinder
platform configuration. Change it by pull request.

| | |
|---|---|
| **Workspace** | `${{ .Inputs.workspace }}` |
| **Repository** | [`${{ .Repo.Organization }}/${{ .Repo.Name }}`](https://github.com/${{ .Repo.Organization }}/${{ .Repo.Name }}) |
| **Tenant** | `${{ .Tenant }}` |

## What is here

| Path | What it is |
|---|---|
| `environments.yaml` | The environment-to-account map. Decides which accounts you need **vended** |
| `manifests/` | Environments, the role bindings saying who may deploy into them, and this team's own identities. Decides what **exists** in Wayfinder |
| `access/` | The IAM roles those identities assume, as a stack deployed into each vended account |
| `.wayfinder/ci.env` | The values that identify this repository to Wayfinder |
| `.github/workflows/plan.yaml` | On a pull request: says what would change |
| `.github/workflows/apply.yaml` | On merge to `main`: creates the roles, then applies the manifests |

## How a change reaches the platform

1. Open a pull request. `plan.yaml` dry-runs `access/Wayfinder.yaml` against every
   environment that has an account, then runs `wf apply --prune --dry-run server` and
   reports what would be created, changed and **removed**.
2. Merge. `apply.yaml` does the same two steps for real.

**The order is not cosmetic.** An `ExternalIdentity` naming an IAM role that does not
exist stops trying to verify after about ten attempts, and nothing retries it afterwards —
so the roles are created first, every time.

`--prune` means **removing a manifest removes the object.** It is scoped to what this
repository owns, so it cannot reap anything else — but within that scope, deleting a file
is a deletion.

## Adding an environment

Add it to **both** `environments.yaml` and `manifests/`. The map says where it deploys; the
manifests say that it exists. Then add a `deployer` role binding per person who should
reach it, following the `${{ .Inputs.firstEnvironment }}` pattern.

Whether you need a new cloud account depends on the `account` value: a new one does, an
existing one does not. Two environments can share an account deliberately. Ask your
platform team to vend the ones you do not have — `environments.yaml` is what you point
them at.

## Your own identities

**A team may grant access within the accounts vended to it, and nowhere else.**

You already hold administrator access in your accounts, through the `aws-<environment>`
identity the vend created. So declaring more identities into one of them widens nothing:
an identity can only trust a role you are able to create, in an account you already
administer. What you still cannot do is reach another team's account or edit the
platform's identities.

Two pieces, and a new identity needs both in the same pull request:

| | |
|---|---|
| `access/Wayfinder.yaml` | The IAM role, its trust policy and its permissions |
| `manifests/identities-<environment>.yaml` | The `ExternalIdentity` naming that role, and who may use it |

The role's name and ARN are decided by the plan, not discovered, so the manifest writes
them out. When this repository was created after its first account was vended, the
manifest already carries the account id. If it is empty under its header, the account
did not exist yet: add the identities in the pull request that records the account in
`environments.yaml`, and `wf apply` skips the file until then.

## Authentication

No secrets. CI authenticates by GitHub Actions OIDC, exchanged for a Wayfinder token
through a federated service-account credential pinned to `refs/heads/main` — which is why a
pull request can only ever plan, never apply.

Two repository or organisation variables are needed:

| Variable | What it is |
|---|---|
| `WAYFINDER_SERVER` | The Wayfinder API URL, e.g. `https://api.example.wayfinder.run` |
| `WAYFINDER_OWNER_EMAIL` | The mailbox a vended account's root email is derived from by subaddressing. Its domain must support that (Google Workspace does) |

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
| `environments.yaml` | The environment-to-account map. Decides what gets **vended** |
| `manifests/` | Environments, and the role bindings saying who may deploy into them. Decides what **exists** in Wayfinder |
| `.wayfinder/ci.env` | The values that identify this repository to Wayfinder |
| `.github/workflows/plan.yaml` | On a pull request: says what would change |
| `.github/workflows/apply.yaml` | On merge to `main`: applies, then vends any missing account |

## How a change reaches the platform

1. Open a pull request. `plan.yaml` runs `wf apply --prune --dry-run server` and reports
   what would be created, changed and **removed**.
2. Merge. `apply.yaml` applies for real with `--prune`, then reads the environment map and
   vends a cloud account for any environment that does not have one yet.

`--prune` means **removing a manifest removes the object.** It is scoped to what this
repository owns, so it cannot reap anything else — but within that scope, deleting a file
is a deletion.

## Adding an environment

Add it to **both** `environments.yaml` and `manifests/`. The map says where it deploys; the
manifests say that it exists. Then add a `deployer` role binding per person who should
reach it, following the `${{ .Inputs.firstEnvironment }}` pattern.

Whether that vends a new cloud account depends on the `account` value: a new one vends, an
existing one does not. Two environments can share an account deliberately.

## What must never be in this repository

- **`ExternalIdentity` objects.** A team can change its own platform configuration but
  cannot grant itself cloud access. Identities live with the platform, and their grant
  names the `deployer` role in an environment — `manifests/bindings.yaml` is what
  decides who holds it.
- **Cloud account ids, role ARNs, or any estate identifier.** Wayfinder is the broker and
  holds those.

## Authentication

No secrets. CI authenticates by GitHub Actions OIDC, exchanged for a Wayfinder token
through a federated service-account credential pinned to `refs/heads/main` — which is why a
pull request can only ever plan, never apply.

Two repository or organisation variables are needed:

| Variable | What it is |
|---|---|
| `WAYFINDER_SERVER` | The Wayfinder API URL, e.g. `https://api.example.wayfinder.run` |
| `WAYFINDER_OWNER_EMAIL` | The mailbox a vended account's root email is derived from by subaddressing. Its domain must support that (Google Workspace does) |

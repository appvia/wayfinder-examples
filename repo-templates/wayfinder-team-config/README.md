# Wayfinder team configuration template

A team's Wayfinder platform configuration as code. It scaffolds the repository a
platform team hands to an application team: the team's environments and the role
bindings saying who may deploy into them, plus the environment-to-account map its
CI reads to decide when a cloud account needs vending.

Unlike the golden paths, this template does not build or deploy an application.
It creates the repository that decides what a team's slice of the platform looks
like, and the CI in it talks to Wayfinder rather than to a registry.

`RepoTemplate-wayfinder-team-config.yaml` is the object you give Wayfinder — a
pointer at this directory. `wayfinder-template.yaml` is the template itself,
which Wayfinder reads once it has followed that pointer. See
[../ANATOMY.md](../ANATOMY.md).

| File | What it is |
| --- | --- |
| `wayfinder-template.yaml` | The template: its inputs, and which files are copied raw. |
| `RepoTemplate-wayfinder-team-config.yaml` | Registers the template with Wayfinder. |
| `skeleton/` | Everything written into the generated repository. |

## The idea

**A team can change its own platform configuration but cannot grant itself cloud
access.** That one line decides everything in the skeleton.

Environments and role bindings live in the team's repository, so the team changes
them by pull request without asking anyone. `ExternalIdentity` objects never do —
an identity's `usableBy` is what decides who reaches a cloud account, so a team
able to edit one could grant itself production. Identities stay with the platform
team.

**Roles are assigned to people directly, not through a group.** The vending
workflow grants the vended identity to `role:deployer@<workspace>/<environment>` —
"whoever holds deployer here" — and `manifests/bindings.yaml` is what decides who
that is. Nothing in between has a name that can be mistyped.

A group would put one there: the platform's grant would have to name the group, and
renaming it in this repository would take away the team's cloud access with nothing
failing at apply time. It would also not work — a grant that names a group always
resolves at tenant scope, and creating a tenant-scoped group needs permissions this
repository's CI is deliberately not given.

## The environment map

`environments.yaml` is separate from `manifests/` on purpose, and the split is
what makes the vend decidable:

- **`manifests/`** says what **exists** in Wayfinder, and who may deploy. Applied
  with `--prune`.
- **`environments.yaml`** says where each environment **deploys to**. Read by CI
  to work out which accounts are missing.

Environments are many-to-one onto accounts, so `test` and `nonprod` can share one.
Adding an environment on an existing account vends nothing; adding one on a new
account vends. Nobody encodes that rule — it falls out of the map.

## What the CI does

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `plan.yaml` | pull request | `wf apply --prune --dry-run server` — reports what would be created, changed and removed |
| `apply.yaml` | merge to `main` | applies with `--prune --confirm` |

The plan is trustworthy rather than advisory because of the credential, not the
command: the CI service account's federated trust is pinned to `refs/heads/main`,
so a pull-request branch cannot apply even if the workflow were edited to try.

**This repository does not vend cloud accounts.** An account is granted by the
platform team, who run their vending workflow against one of your environments.
It creates an `ExternalIdentity` and grants it to a role; `manifests/bindings.yaml`
is where you decide who holds that role, so the team still decides who reaches
what. `environments.yaml` records which account each environment expects, and is
what you point the platform team at when you need one that does not exist yet.

Keeping the vend out of here is why the CI service account needs nothing outside
its own workspace. A job that invoked the platform's vending workflow would need
manager access to the platform's workspace — which would let this repository edit
the vending workflow itself, and a team could then grant itself cloud access.

## Using it

```bash
wf apply -f RepoTemplate-wayfinder-team-config.yaml

wf create coderepo wayfinder-payments -w payments \
  --from-template wayfinder-team-config \
  --input team=payments --input workspace=payments \
  --input serviceAccount=wayfinder-payments-ci \
  --dry-run
```

Drop `--dry-run` and add `--github-org` to create the repository for real.

## What has to exist first

This template is the second half of an onboarding flow. Scaffolding it on its own
produces a repository whose CI fails, because the things its CI authenticates as
and calls do not exist yet:

| | |
| --- | --- |
| The workspace and the first environment | named by `workspace` and `firstEnvironment` |
| A CI service account, with a **federated** credential pinned to `refs/heads/main` | named by `serviceAccount` |
| A `deployer` role binding for that service account in the workspace | so `wf apply` may write |

The `onboard-aws` example in the Wayfinder repository creates all three. Leave `main` unprotected on the scaffolded repository: if a
ruleset protects it, Wayfinder correctly opens a pull request instead of
committing to `main`, so nothing lands, no CI fires, and onboarding quietly stops
being automatic.

## Repository variables

**Nothing here has to be set for a scaffolded repository to work.** Wayfinder cannot
set a GitHub variable when it scaffolds, so anything the CI could only get from one
would put a person back in the middle of a flow whose point is that there is not
one. Every value the CI needs therefore arrives as a template input and is written
into `.wayfinder/ci.env`.

These variables exist for installations that would rather hold a value in one place
for every team repository. Each **overrides** the scaffolded value, so setting one on
the organisation is a choice, not a prerequisite.

| Variable | Overrides | What it is |
| --- | --- | --- |
| `WAYFINDER_SERVER` | the `wayfinderServer` input | The Wayfinder API URL. With neither set, the CLI uses its own default, which is the Wayfinder SaaS API |

Both CI jobs run **inside the Wayfinder toolbox image**, which already carries `wf`,
so neither downloads a tool. The tag is written into the workflows
rather than taken from a variable: Wayfinder cannot set a GitHub variable when it
scaffolds, so every variable the template needs is a manual step in a flow whose
point is that there is not one.

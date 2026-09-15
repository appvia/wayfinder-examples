# Wayfinder team configuration template

A team's Wayfinder platform configuration as code. It scaffolds the repository a
platform team hands to an application team: the team's environments, groups and
role bindings, plus the environment-to-account map its CI reads to decide when a
cloud account needs vending.

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

Environments, deployer groups and role bindings live in the team's repository, so
the team changes them by pull request without asking anyone. `ExternalIdentity`
objects never do — an identity's `usableBy` is what decides who reaches a cloud
account, so a team able to edit one could grant itself production. Identities stay
with the platform team, and the team's repository only ever names the *group* that
a platform-side grant points at.

That is why `manifests/groups.yaml` carries a warning about its own name. The
group name is a contract with the vending workflow, which grants the vended
identity to `<workspace>-<environment>-deployers`. Rename it and the team loses
access to its own cloud account with no error at apply time — the grant simply
names a group that no longer exists.

## The environment map

`environments.yaml` is separate from `manifests/` on purpose, and the split is
what makes the vend decidable:

- **`manifests/`** says what **exists** in Wayfinder. Applied with `--prune`.
- **`environments.yaml`** says where each environment **deploys to**. Read by CI
  to work out which accounts are missing.

Environments are many-to-one onto accounts, so `test` and `nonprod` can share one.
Adding an environment on an existing account vends nothing; adding one on a new
account vends. Nobody encodes that rule — it falls out of the map.

## What the CI does

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `plan.yaml` | pull request | `wf apply --prune --dry-run server` — reports what would be created, changed and removed |
| `apply.yaml` | merge to `main` | applies with `--prune --confirm`, then vends an account for any environment whose identity is not `Verified` |

The plan is trustworthy rather than advisory because of the credential, not the
command: the CI service account's federated trust is pinned to `refs/heads/main`,
so a pull-request branch cannot apply even if the workflow were edited to try.

**`Verified` is the vend test, not existence.** An `ExternalIdentity` can exist
while being completely broken — a vend that reached identity registration with bad
outputs leaves one behind — and `wf get externalidentity` exits 0 for it either
way. Testing existence would report "already vended" and skip the vend forever.
`Verified` comes from a real `AssumeRoleWithWebIdentity`, so it is the only honest
answer to "did the whole vend finish".

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
| A vending workflow the CI may invoke | named by `vendWorkflow`, in `platformWorkspace` |

The `onboard-aws` example in the Wayfinder repository creates the first three and
provides the fourth. Leave `main` unprotected on the scaffolded repository: if a
ruleset protects it, Wayfinder correctly opens a pull request instead of
committing to `main`, so nothing lands, no CI fires, and onboarding quietly stops
being automatic.

## Repository variables

Two, set on the repository or the organisation:

| Variable | What it is |
| --- | --- |
| `WAYFINDER_SERVER` | The Wayfinder API URL, e.g. `https://api.example.wayfinder.run` |
| `WAYFINDER_OWNER_EMAIL` | The mailbox a vended account's root email is derived from by subaddressing. Its domain must support that (Google Workspace does) |

They are variables rather than template inputs because they differ per Wayfinder
installation, not per repository — the same reason the golden paths keep their
server URL out of `.wayfinder/ci.env`.

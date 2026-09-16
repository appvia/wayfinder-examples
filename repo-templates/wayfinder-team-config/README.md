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

**A team may grant access within the accounts vended to it, and nowhere else.** That
one line decides everything in the skeleton.

Environments, role bindings and the team's own identities live in the team's
repository, so the team changes them by pull request without asking anyone. The
identities are not a hole in that: the team already holds administrator access in its
vended account, through the `aws-<environment>` identity the vend created. Declaring
more identities into that account widens nothing — an identity can only trust a role
the team is able to create, in an account it already administers. What the team still
cannot do is reach another team's account, or edit the platform's identities.

**Where that line sits is the platform team's choice, and it is drawn in IAM, not
here.** The policy on the vended `wf-deploy` role is the whole statement of what the
team was permitted: `AdministratorAccess` includes creating roles, so it includes
declaring identities; a policy without `iam:CreateRole` does not, and this
repository's `access/` stack is then refused at deploy time rather than at review. In
between sits a permissions boundary, which lets a team create roles while capping what
any of them may do. A rule enforced by the cloud holds whichever tool the team uses,
a laptop, this CI or an agent's sandbox, which is why Wayfinder does not enforce one
of its own. An `ExternalIdentity` is only a pointer at a role: one that names a role
the team could not create stays Unverified and grants nothing.

The identity the vend delivered, `aws-<environment>`, is deliberately not in this
repository. It was handed to the team, not declared by it, and keeping it out of
`--prune`'s reach means a bad merge here cannot remove the grant this repository's
own CI deploys through.

What the team cannot do at all is *get* an account. Vending stays with the platform
team, and `environments.yaml` is the record of what this team is asking for.

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

## The team's own identities

`access/` and `manifests/identities-<environment>.yaml` are two halves of one thing,
and a new identity needs both in the same pull request:

- **`access/plans/aws-wayfinder-roles.yaml`** creates an IAM role per identity,
  `wf-<workspace>-<environment>-<name>`, trusting only the OIDC subject Wayfinder will
  present for the identity of that name. `access/Wayfinder.yaml` is the stack that
  deploys it, through the environment's own `aws-<environment>` identity.
- **`manifests/identities-<environment>.yaml`** creates the `ExternalIdentity` naming
  that role, and says who may use it.

Nothing flows from the Terraform into the manifest. A role's name and ARN follow from
the account id and a name the team chose, so the manifest writes them out — which is
also why the manifest ships with the literal `ACCOUNT_ID`: the repository is scaffolded
before the account is vended, so there is no id to write yet.

The two starters are `aws-readonly`, granted to whoever holds `deployer` in the
environment, and `module-upgrade-inspector`, granted to the `module-upgrade-prove` AI
agent alone. Both are `usableFor: [directaccess]` and neither is `tfprovisioning`, so
neither can be deployed through.

## What the CI does

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `plan.yaml` | pull request | dry-runs `access/Wayfinder.yaml` per environment, then `wf apply --prune --dry-run server` |
| `apply.yaml` | merge to `main` | deploys `access/Wayfinder.yaml` per environment, then applies with `--prune --confirm` |

**The roles come before the manifests, and that order is not cosmetic.** An
`ExternalIdentity` naming an IAM role that does not exist stops trying to verify after
about ten attempts, and nothing retries it afterwards — so a manifest applied first
would sit unverified until somebody noticed.

Both jobs read `environments.yaml` and skip any environment whose `aws-<environment>`
identity does not exist yet, printing that it has no vended account. An identities
manifest still carrying `ACCOUNT_ID` is left out of the apply the same way — but if
that environment *does* have an account, the job fails instead and names the file.
Failing rather than skipping is what makes leaving it out safe: `--prune` reaps owned
objects absent from the fileset, so a file that is silently dropped after it has been
applied once would delete the identities in it.

The plan is trustworthy rather than advisory because of the credential, not the
command: the CI service account's federated trust is pinned to `refs/heads/main`,
so a pull-request branch cannot apply even if the workflow were edited to try.

The CI service account needs nothing beyond what onboarding already gives it.
`workspace.manager` in the team's workspace includes `deployer` — so it holds
`deployer` in every environment, which is the role the vend grants the
`aws-<environment>` identity to — and `externalidentities.management`, which permits
creating an `ExternalIdentity` at environment scope.

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

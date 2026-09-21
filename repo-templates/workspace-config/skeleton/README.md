# ${{ .Inputs.workspaceName }} configuration

${{ .Inputs.description }}

This repository holds the Wayfinder configuration for the
`${{ .Inputs.workspaceName }}` workspace. It is not a stack: nothing here is
deployed. CI applies what is in `config/` to Wayfinder.

## What is here

| Path | What it is |
| --- | --- |
| `config/` | The Wayfinder objects this workspace owns, applied with `wf apply` |
| `.github/workflows/apply.yaml` | Applies `config/` when a change merges |

## How CI signs in

Scaffolding created a Wayfinder service account, `${{ .Repo.Name }}-configurer`,
holding `workspace.manager` in `${{ .Inputs.workspaceName }}`. It signs in with a
federated credential, so there is no token to store: GitHub Actions presents its
own identity and Wayfinder exchanges it.

The workflow declares the `apply` environment, which is what the credential
accepts. A job without it is refused.

## Changing the configuration

Edit a file under `config/`, open a pull request, and merge it. The workflow
applies it. Nothing is applied from a branch.

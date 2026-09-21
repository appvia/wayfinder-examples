# Configuration for ${{ .Inputs.workspaceName }}

Put the Wayfinder objects this workspace owns here. CI runs `wf apply -f` over
this directory when a change merges.

Anything `wf apply` accepts belongs here, for example:

- `Environment` — the environments this workspace deploys into
- `RoleBinding` — who holds what, and where
- variables and secrets shared across the workspace

One object per file, named after it, so a change reads as what it changes.

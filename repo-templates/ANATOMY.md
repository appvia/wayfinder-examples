# The two files, and which is which

Every template here has two YAML files with confusingly similar names. They do
completely different jobs, and knowing which is which makes everything else
obvious.

```
repo-templates/go-app/
├── RepoTemplate-go-app.yaml   ← you give this to Wayfinder
├── wayfinder-template.yaml    ← Wayfinder reads this from the repository
└── skeleton/                  ← what gets copied into the new repository
```

## `RepoTemplate-*.yaml` — the one you apply

A Wayfinder object, like any other. It is a **pointer**: which repository, which
branch or tag, and which directory inside it.

```yaml
apiVersion: sourcecontrol/v3
kind: RepoTemplate
metadata:
  name: go-app
spec:
  url: https://github.com/appvia/wayfinder-examples.git
  path: repo-templates/go-app
  ref: main
  displayName: Go service
  category: service
```

```bash
wf apply -f repo-templates/go-app/RepoTemplate-go-app.yaml
```

It carries no inputs and no file list, because it does not know any of that yet.
All it knows is where to look. `displayName` and `category` are the catalogue's
business — how the template is filed and filtered — rather than anything about
the template's content.

## `wayfinder-template.yaml` — the one in the repository

Not a Wayfinder object at all. No `apiVersion`, no `kind`. This is the template
*itself*: what it is called, what it asks the user for, and which of its files
are copied verbatim.

```yaml
name: Go service
description: >-
  A Go HTTP service on net/http, deployed by a Wayfinder stack...
icon: go
inputs:
  - name: serviceName
    type: string
    pattern: "^[a-z][a-z0-9-]*[a-z0-9]$"
skeleton:
  path: skeleton
  raw:
    - ".github/workflows/*.yaml"
```

**It contains no repository path, deliberately.** It does not know where it
lives, and that is the point — the same file works read from a branch, a tag, a
fork, or a different directory. Putting the path in both files would only give
you two things that can disagree.

## How they meet

1. You apply the `RepoTemplate`.
2. Wayfinder clones `spec.url` at `spec.ref` and looks in `spec.path`.
3. It finds `wayfinder-template.yaml` there and parses it.
4. It caches the result in the object's `status.manifest`, along with the exact
   commit it read, and re-checks about once an hour.
5. `wf create stack --from-template` prompts for the inputs that manifest
   declares, renders `skeleton/` with the answers, and pushes the result as the
   first commit of a new repository.

Scaffolding always uses the pinned `status.commit`, not the tip of the branch —
so what a user previewed is what they get, even if the template moves in between.

```bash
wf get repotemplate go-app -o yaml     # status.manifest is step 4
```

If `status.manifest` is empty, Wayfinder could not read the template: usually a
wrong `spec.path`, a private repository, or a manifest that failed to parse.
The object's status message says which.

## The rest of the directory

`skeleton/` is the only part that becomes the new repository. The
`RepoTemplate-*.yaml`, the `wayfinder-template.yaml` and the template's own
`README.md` all sit outside it and are never copied.

Inside `skeleton/`, both file *contents* and file *paths* are rendered — which is
why you will see directories like `cmd/${{ .Inputs.serviceName }}/` on disk. A
path that renders to nothing is how a file is conditionally left out.

The exception is `skeleton.raw`: those files are copied byte for byte. That
matters for anything with its own `${{ }}` syntax — GitHub Actions workflows,
Helm charts, CloudResourcePlans — which Wayfinder would otherwise try to
evaluate. There are two ways to protect them and both are in use here on
purpose; see the [README](./README.md) for which template demonstrates which.

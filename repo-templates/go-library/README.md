# Go library template

A Go module with tests and CI, for shared code rather than a deployable service.

Register it:

```bash
wf apply -f repo-templates/go-library/RepoTemplate-go-library.yaml
```

`RepoTemplate-go-library.yaml` is the object you give Wayfinder — a pointer at
this directory. `wayfinder-template.yaml` is the template itself, which
Wayfinder reads once it has followed that pointer. Neither is scaffolded; only
`skeleton/` is. See [../ANATOMY.md](../ANATOMY.md).

## Inputs

| Input | Type | Required | Default | Notes |
| ----- | ---- | -------- | ------- | ----- |
| `packageName` | string | yes | — | `^[a-z][a-z0-9]*$`. Used for the directory and the package clause |

## What the skeleton exercises

The smallest useful template: one input, a handful of files, and no deployment
at all.

- **No `Wayfinder.yaml`** — not everything scaffolded is a deployable service. A
  library is never deployed, so it declares no stack, and `wf up` has nothing to
  do with it. The template still saves the setup.
- **Templated path** — `${{ .Inputs.packageName }}/` becomes the package
  directory.
- **`$${{` escaping** — the CI workflow escapes GitHub Actions' expressions so
  they survive rendering, including a build matrix over two Go versions.

Because it deploys nothing, this is also the quickest template to try end to
end: nothing needs a cloud account, a cluster or a plan.

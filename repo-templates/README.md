# Repository templates

Wayfinder [repository templates](https://on.wayfinder.run/docs/repo-templates/01-overview):
repositories Wayfinder copies to create new ones, filling in the inputs whoever
creates the repository supplies.

| Template | What it creates | What it shows |
| -------- | --------------- | ------------- |
| [`go-microservice/`](./go-microservice) | A minimal Go service deploying a cloud resource | The worked example the docs walk through file by file. Keeps a CI workflow verbatim with a **`raw` glob** |
| [`go-service/`](./go-service) | A Go HTTP service with its own Helm chart and a deployable `Wayfinder.yaml` | The same problem the other way: **`$${{` escaping** so the workflow can be templated too, plus a chart deployed with `chartPath` |
| [`go-library/`](./go-library) | A Go module with tests and CI | The smallest useful template: one input, no deployment |

The first two overlap deliberately. A skeleton that ships GitHub Actions has to
stop Wayfinder resolving Actions' own `${{ }}` expressions, and there are two
ways to do it — exempt the file's contents entirely with `skeleton.raw`, or
escape each expression with `$${{` and keep templating the rest of the file. One
template demonstrates each.

## Try one

These are registered from this public repository, so Wayfinder reads them
anonymously — no GitHub connection is needed to register or preview a template.
Creating a repository from one does need a connected GitHub organisation.

```bash
# Register the template in your tenant's catalogue
$ wf apply -f - <<'EOF'
apiVersion: sourcecontrol/v3
kind: RepoTemplate
metadata:
  name: go-service
  labels:
    language: go
spec:
  url: https://github.com/appvia/wayfinder-examples.git
  path: repo-templates/go-service
  ref: main
  category: service
EOF

# Check Wayfinder could read it
$ wf get repotemplate go-service -o yaml

# See what it produces, creating nothing
$ wf create stack payments --from-template go-service --input serviceName=payments --dry-run

# Create the repository and the stack for real
$ wf create stack payments --from-template go-service --input serviceName=payments
```

## How a template is laid out

```
go-service/
├── wayfinder-template.yaml   the template's name, description and inputs
└── skeleton/                 the files that get copied, rendered with the inputs
```

`wayfinder-template.yaml` is the definition, and it lives here rather than in
Wayfinder — so the inputs and the files they fill in are versioned together.

Registration points at the **directory** holding the manifest (`path` above),
which is why one repository can hold both of these templates.

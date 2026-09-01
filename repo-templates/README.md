# Repository templates

Wayfinder [repository templates](https://on.wayfinder.run/docs/repo-templates/01-overview):
repositories Wayfinder copies to create new ones, filling in the inputs whoever
creates the repository supplies.

There are two sets here, and they are for different things.

## Golden paths

Production-shaped services. Each one scaffolds a repository that builds a
container image, deploys itself through Wayfinder, and arrives with a working
delivery pipeline: pull request previews, deploy to develop on merge, deploy to
production on a tag.

| Template | Language | What you get |
| -------- | -------- | ------------ |
| [`go-app/`](./go-app) | Go | `net/http` service, distroless static image |
| [`python-app/`](./python-app) | Python | FastAPI on uv, venv on `python:3.13-slim` |
| [`node-app/`](./node-app) | TypeScript | Fastify on `node:22-slim` |
| [`java-app/`](./java-app) | Java | Spring Boot on Maven, `eclipse-temurin:21-jre` |
| [`dotnet-app/`](./dotnet-app) | C# | ASP.NET Core minimal APIs, `aspnet:10.0-noble-chiseled` |

They share one CI contract, one set of inputs and one stack shape, so moving
between them is only a change of language. The stack is an app workload on
Kubernetes plus an S3 bucket, wired together by workload identity — the pod
reaches the bucket through its own cloud identity, and no credentials exist
anywhere in the generated repository.

**[DELIVERY-PIPELINE.md](./DELIVERY-PIPELINE.md) is the setup guide** — what the
workflows do, the service accounts they need, and the GitHub variables to set.
Read it before scaffolding one, because a template that deploys needs more
groundwork than one that does not.

## Teaching examples

Smaller templates that each demonstrate one mechanic, and are what the docs walk
through.

| Template | What it creates | What it shows |
| -------- | --------------- | ------------- |
| [`go-microservice/`](./go-microservice) | A minimal Go service deploying a cloud resource | The worked example the docs walk through file by file. Keeps a CI workflow verbatim with a **`raw` glob** |
| [`go-service/`](./go-service) | A Go HTTP service with its own Helm chart and a deployable `Wayfinder.yaml` | The same problem the other way: **`$${{` escaping** so the workflow can be templated too, plus a chart deployed with `chartPath` |
| [`go-library/`](./go-library) | A Go module with tests and CI | The smallest useful template: one input, no deployment |

The first two overlap deliberately. A skeleton that ships GitHub Actions has to
stop Wayfinder resolving Actions' own `${{ }}` expressions, and there are two
ways to do it — exempt the file's contents entirely with `skeleton.raw`, or
escape each expression with `$${{` and keep templating the rest of the file. One
template demonstrates each. The golden paths use the `raw` approach, which keeps
their four workflows byte-identical across every language.

## Try one

These are registered from this public repository, so Wayfinder reads them
anonymously — no GitHub connection is needed to register or preview a template.
Creating a repository from one does need a connected GitHub organisation.

```bash
# Register the template in your tenant's catalogue
$ wf apply -f repo-templates/go-app/RepoTemplate-go-app.yaml

# Check Wayfinder could read it
$ wf get repotemplate go-app -o yaml

# See what it produces, creating nothing
$ wf create stack payments --from-template go-app --input serviceName=payments --dry-run

# Create the repository and the stack for real
$ wf create stack payments --from-template go-app --input serviceName=payments \
    --github-org github.acme --workspace team-a
```

`serviceName` is the only required input on every template here. Run
`wf get repotemplate <name> -o yaml` to see the rest with their defaults.

## How a template is laid out

```
go-app/
├── RepoTemplate-go-app.yaml   you give this to Wayfinder — a pointer
├── wayfinder-template.yaml    Wayfinder reads this from the repository
└── skeleton/                  the files that get copied into the new repository
```

Two YAML files with similar names doing completely different jobs.
**[ANATOMY.md](./ANATOMY.md) explains which is which**, and is the thing to read
first if you have not worked with these before.

The short version: the `RepoTemplate` says *where the template is*, the
`wayfinder-template.yaml` at that path says *what the template is*. The manifest
carries no repository path deliberately, so the same file works read from a
branch, a tag or a fork.

Registration points at the **directory** holding the manifest (`spec.path`),
which is why one repository can hold all of these templates.

## Working on the templates

- **Language differences live in the `Makefile`.** The golden paths' CI calls
  `make ci-setup`, `make test`, `make lint` and `make build` and nothing else.
  That is what lets the four workflows stay identical across languages.
- **All the golden paths ship the same workflows.** Edit
  [`_shared/workflows/`](./_shared/workflows), copy the result into each one, and
  CI checks they still match.
- **`Wayfinder.yaml` is the one file mixing engines.** It is rendered, so every
  Wayfinder *stack* expression in it must be written `$${{ ... }}` to survive as
  a literal `${{ ... }}`. Get this wrong and it fails when someone creates a
  repository — check with `--dry-run` before merging.
- **Raw glob gotchas.** They are matched with Go's `path.Match`: there is no
  `**`, a `*` never crosses a `/`, and `*.yaml` does not match `*.yml`.

### Checking your work

```bash
# Structure, glob coverage, escaping and workflow drift. No server needed.
pip install pyyaml && python3 repo-templates/hack/check-templates.py

# Compile and test one skeleton locally.
repo-templates/hack/render-for-build.sh repo-templates/go-app /tmp/out
cd /tmp/out && make ci-setup && make lint && make test
```

CI runs both of those, plus `actionlint`, `helm lint` and a container build for
every golden path. The full render, using Wayfinder's own template engine, runs
from the wayfinder repository:

```bash
WF_EXAMPLES_DIR=$PWD go test -count=1 \
  ./server/sourcecontrol/services/repotemplates/ -run Example
```

Pass `-count=1` — the templates live outside that module, so editing one does
not invalidate Go's test cache and a stale pass is easy to believe.

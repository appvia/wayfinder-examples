# ${{ .Inputs.serviceName }}

${{ .Inputs.description }}

| | |
|---|---|
| **Stack** | `${{ .Stack.Name }}` |
| **Repository** | [`${{ .Repo.Organization }}/${{ .Repo.Name }}`](${{ .Repo.URL }}) |
| **Default branch** | `${{ .Repo.DefaultBranch }}` |

Scaffolded by Wayfinder from a RepoTemplate.

## Run

```bash
go run ./cmd/${{ .Inputs.serviceName }}
```

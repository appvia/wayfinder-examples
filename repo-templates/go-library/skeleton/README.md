# ${{ .Repo.Name }}

A Go library. Created from the `go-library` repository template in Wayfinder.

```go
import "github.com/${{ .Repo.Organization }}/${{ .Repo.Name }}/${{ .Inputs.packageName }}"
```

## Develop

```bash
go test ./...
```

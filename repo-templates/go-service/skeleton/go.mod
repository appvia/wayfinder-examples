module github.com/${{ .Repo.Organization }}/${{ .Repo.Name }}

go ${{ .Inputs.goVersion }}

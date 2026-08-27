FROM golang:${{ .Inputs.goVersion }}-alpine AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /out/${{ .Inputs.serviceName }} ./cmd/${{ .Inputs.serviceName }}

FROM gcr.io/distroless/static:nonroot
COPY --from=build /out/${{ .Inputs.serviceName }} /${{ .Inputs.serviceName }}
EXPOSE ${{ .Inputs.port }}
USER nonroot:nonroot
ENTRYPOINT ["/${{ .Inputs.serviceName }}"]

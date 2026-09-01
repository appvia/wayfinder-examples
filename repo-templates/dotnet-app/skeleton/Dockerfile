# syntax=docker/dockerfile:1

# ---- Build -----------------------------------------------------------------
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build

WORKDIR /src

# Project files first, so a source-only change reuses the cached restore.
COPY src/Service/Service.csproj src/Service/
RUN dotnet restore src/Service/Service.csproj

COPY src ./src

# VERSION is passed by CI: the git tag for a release, the short SHA otherwise.
ARG VERSION=dev
# VERSION is unset for this one command. Docker turns a build arg into an
# environment variable, MSBuild reads environment variables as properties, and
# it would take a value like "v1.2.3" or "sha-abc1234" as the assembly Version —
# which is not a valid NuGet version and fails the publish. The release still
# reaches the running service through ENV RELEASE below.
RUN VERSION= dotnet publish src/Service/Service.csproj \
      --configuration Release \
      --no-restore \
      -o /publish

# ---- Run -------------------------------------------------------------------
# Chiseled: an Ubuntu base cut down to what the runtime needs, with no shell and
# no package manager, and it runs as a non-root user out of the box.
FROM mcr.microsoft.com/dotnet/aspnet:10.0-noble-chiseled

ARG VERSION=dev
ENV RELEASE=${VERSION} \
    DOTNET_RUNNING_IN_CONTAINER=true

WORKDIR /app
COPY --from=build /publish .

USER $APP_UID
EXPOSE ${{ .Inputs.port }}

ENTRYPOINT ["dotnet", "Service.dll"]

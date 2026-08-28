"""${{ .Inputs.description }}

Serves three endpoints:

    GET /         the service identity and release
    GET /healthz  liveness  — is the process alive
    GET /readyz   readiness — is the process ready to take traffic

The liveness and readiness endpoints are what the Helm chart in charts/app
wires up as probes, so keep them cheap and dependency-free.
"""

import os

from fastapi import FastAPI

# Set at build time by CI: the git tag for a release, the short SHA otherwise,
# so a running pod can always be traced back to a commit.
RELEASE = os.environ.get("RELEASE", "dev")

app = FastAPI(title="${{ .Inputs.serviceName }}", version=RELEASE)


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/readyz")
def readyz() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/")
def root() -> dict[str, str]:
    # BUCKET_NAME is set by the Helm chart from the storage component's output.
    # The workload reaches the bucket through its own cloud identity, so there
    # are no credentials to read here.
    return {
        "service": "${{ .Inputs.serviceName }}",
        "release": RELEASE,
        "bucket": os.environ.get("BUCKET_NAME", ""),
    }

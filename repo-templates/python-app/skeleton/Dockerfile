# syntax=docker/dockerfile:1

# ---- Build -----------------------------------------------------------------
FROM python:3.13-slim AS build

# renovate: datasource=github-releases depName=astral-sh/uv
COPY --from=ghcr.io/astral-sh/uv:0.11.14 /uv /uvx /bin/

ENV UV_LINK_MODE=copy \
    UV_COMPILE_BYTECODE=1 \
    UV_PYTHON_DOWNLOADS=never

WORKDIR /src

# Dependencies first, so a source-only change reuses the cached layer.
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project

COPY app ./app
RUN uv sync --locked --no-dev

# ---- Run -------------------------------------------------------------------
FROM python:3.13-slim

# VERSION is passed by CI: the git tag for a release, the short SHA otherwise.
ARG VERSION=dev
ENV RELEASE=${VERSION} \
    PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

RUN useradd --create-home --uid 10001 app
WORKDIR /app

COPY --from=build --chown=app:app /src/.venv /app/.venv
COPY --chown=app:app app ./app

USER app
EXPOSE ${{ .Inputs.port }}

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-${{ .Inputs.port }}}"]

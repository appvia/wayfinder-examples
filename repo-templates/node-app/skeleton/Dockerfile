# syntax=docker/dockerfile:1

# ---- Dependencies ----------------------------------------------------------
FROM node:22-slim AS deps

WORKDIR /src

# Lockfile first, so a source-only change reuses the cached install layer.
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# ---- Run -------------------------------------------------------------------
FROM node:22-slim

# VERSION is passed by CI: the git tag for a release, the short SHA otherwise.
ARG VERSION=dev
ENV RELEASE=${VERSION} \
    NODE_ENV=production

WORKDIR /app

COPY --from=deps --chown=node:node /src/node_modules ./node_modules
COPY --chown=node:node package.json ./
COPY --chown=node:node src ./src

# tsx is a dev dependency, so the runtime image strips types with Node's own
# loader rather than carrying a TypeScript toolchain into production.
USER node
EXPOSE ${{ .Inputs.port }}

CMD ["node", "--experimental-strip-types", "src/server.ts"]

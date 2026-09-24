#!/usr/bin/env bash
#
# Creates the three Wayfinder service accounts a scaffolded repository needs,
# one per environment, each trusting a different GitHub OIDC subject.
#
#   usage: TENANT=acme WORKSPACE=team-a REPO=acme/payments SERVICE=payments \
#            DEVELOP_ENV=dev PREVIEW_ENV=dev PROD_ENV=prod \
#            ./setup-ci-service-accounts.sh
#
# The separation is the point: the credential a pull request can use must not be
# able to deploy to production. Nothing long-lived is stored in the repository —
# GitHub mints a short-lived identity token per workflow run and Wayfinder
# exchanges it, so there is no secret to leak or rotate.
#
# THE TRUST IS DESCRIBED BY CLAIMS, NOT BY THE SUBJECT, and that is not a style
# choice. GitHub issues repositories created from a template an IMMUTABLE OIDC
# subject carrying numeric ids:
#
#   repo:acme@33072293/payments@12345678:environment:production
#
# `wf create serviceaccountcredential --github-repo` builds `repo:acme/payments:…`,
# which can never match that. So the subject here is a prefix wildcard covering
# both forms, and the exact-match claims below are what actually pin the trust to
# one repository and one stage.
set -euo pipefail

TENANT="${TENANT:?set TENANT, e.g. acme}"
WORKSPACE="${WORKSPACE:?set WORKSPACE, e.g. team-a}"
REPO="${REPO:?set REPO in owner/repo form, e.g. acme/payments}"
SERVICE="${SERVICE:?set SERVICE, the serviceName you scaffolded with}"

DEVELOP_ENV="${DEVELOP_ENV:?set DEVELOP_ENV, the developEnvironment you scaffolded with}"
PREVIEW_ENV="${PREVIEW_ENV:?set PREVIEW_ENV, the previewEnvironment you scaffolded with}"
PROD_ENV="${PROD_ENV:?set PROD_ENV, the prodEnvironment you scaffolded with}"

ISSUER="https://token.actions.githubusercontent.com"
# Matches both `repo:acme/payments:…` and `repo:acme@33072293/payments@12345678:…`.
SUBJECT="repo:${REPO%%/*}*"

# account name : environment : the claims it requires beyond the repository
#
# event_name=pull_request  matches only workflow runs triggered by a pull request.
# environment=<name>       matches only a job that declares that environment, so
#                          the GitHub approval gate is enforced by the token
#                          itself rather than only by the UI.
create() {
  local name="$1" env="$2"
  shift 2

  echo "==> ${name} (${env})"
  wf create serviceaccount "${name}" -w "${WORKSPACE}" || true

  wf create serviceaccountcredential "github" \
    --service-account "${TENANT}:${WORKSPACE}:${name}" \
    --issuer "${ISSUER}" \
    --subject "${SUBJECT}" \
    --claim "repository=${REPO}" \
    "$@"

  wf grant role deployer \
    --to "ServiceAccount:${TENANT}:${WORKSPACE}:${name}" \
    -w "${WORKSPACE}" -e "${env}"
}

create "${SERVICE}-ci-preview" "${PREVIEW_ENV}" --claim "event_name=pull_request"
create "${SERVICE}-ci-develop" "${DEVELOP_ENV}" --claim "environment=develop"
create "${SERVICE}-ci-prod" "${PROD_ENV}" --claim "environment=production"

# Wayfinder.yaml refers to its plans as file:plans/…, and Wayfinder publishes
# each one to the workspace catalogue as it deploys — including on the dry run
# the validate job does. The deployer role does not carry that, so every account
# that runs a deploy or a dry run needs it as well, at workspace scope.
for stage in preview develop prod; do
  wf grant role workspace.cataloguemanagement \
    --to "ServiceAccount:${TENANT}:${WORKSPACE}:${SERVICE}-ci-${stage}" \
    -w "${WORKSPACE}"
done

cat <<MSG

Done. One thing is still needed before CI can deploy:

  GitHub environments named 'develop' and 'production' must exist on ${REPO},
  with your reviewers on 'production'. Without them GitHub will not mint the
  environment-scoped token these credentials trust.
MSG

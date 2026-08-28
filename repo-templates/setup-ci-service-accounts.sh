#!/usr/bin/env bash
#
# Creates the three Wayfinder service accounts a scaffolded repository needs,
# one per environment, each trusting a different GitHub OIDC subject.
#
#   usage: TENANT=acme WORKSPACE=team-a REPO=acme/payments SERVICE=payments \
#            ./setup-ci-service-accounts.sh
#
# The separation is the point: the credential a pull request can use must not be
# able to deploy to production. Nothing long-lived is stored in the repository —
# GitHub mints a short-lived identity token per workflow run and Wayfinder
# exchanges it, so there is no secret to leak or rotate.
set -euo pipefail

TENANT="${TENANT:?set TENANT, e.g. acme}"
WORKSPACE="${WORKSPACE:?set WORKSPACE, e.g. team-a}"
REPO="${REPO:?set REPO in owner/repo form, e.g. acme/payments}"
SERVICE="${SERVICE:?set SERVICE, the serviceName you scaffolded with}"

DEVELOP_ENV="${DEVELOP_ENV:-dev}"
PREVIEW_ENV="${PREVIEW_ENV:-dev}"
PROD_ENV="${PROD_ENV:-prod}"

# account name : environment : the GitHub subject restriction it trusts
#
# --github-pull-request  matches only workflow runs triggered by a pull request.
# --github-environment   matches only a job that declares that environment, so
#                        the GitHub approval gate is enforced by the token
#                        itself rather than only by the UI.
create() {
  local name="$1" env="$2"
  shift 2

  echo "==> ${name} (${env})"
  wf create serviceaccount "${name}" -w "${WORKSPACE}" || true

  wf create serviceaccountcredential "github" \
    --service-account "${TENANT}:${WORKSPACE}:${name}" \
    --github-repo "${REPO}" \
    "$@"

  wf grant role deployer \
    --to "ServiceAccount:${TENANT}:${WORKSPACE}:${name}" \
    -w "${WORKSPACE}" -e "${env}"
}

create "${SERVICE}-ci-preview" "${PREVIEW_ENV}" --github-pull-request
create "${SERVICE}-ci-develop" "${DEVELOP_ENV}" --github-environment develop
create "${SERVICE}-ci-prod" "${PROD_ENV}" --github-environment production

cat <<EOF

Done. Two things are still needed before CI can deploy:

  1. The preview account needs catalogue write in ${WORKSPACE}. Wayfinder.yaml
     refers to its plan as file:plans/..., which publishes that plan to the
     workspace catalogue — including on the dry run the validate job does.
     Grant it, or point the storagePlan input at a catalogue reference instead.

  2. GitHub environments named 'develop' and 'production' must exist on
     ${REPO}, with your reviewers on 'production'. Without them GitHub will not
     mint the environment-scoped token these credentials trust.
EOF

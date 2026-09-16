#!/usr/bin/env bash
#
# Resolves the Wayfinder context for one CI stage and exports it to the job.
#
#   usage: .wayfinder/ci-env.sh <preview|develop|prod>
#
# Reads .wayfinder/ci.env, which Wayfinder wrote when this repository was
# scaffolded, combines it with the WF_* GitHub variables set on the repository
# or organisation, and writes the result to $GITHUB_ENV so later steps can use
# it directly. Deployment target flags are written one per line to a file whose
# path lands in WF_DEPLOY_ARGS_FILE; read them with `mapfile -t args < "$FILE"`.
#
# Exports:
#   WAYFINDER_TENANT, WAYFINDER_WORKSPACE, WAYFINDER_ENVIRONMENT
#   WAYFINDER_SERVICE_ACCOUNT  the CLI authenticates with this implicitly
#   WF_INSTANCE                the stack instance for this stage
#   WF_DEPLOY_ARGS_FILE        --identity/--region/--dns-zone flags
#   plus everything in .wayfinder/ci.env
set -euo pipefail

stage="${1:-}"
if [[ -z "${stage}" ]]; then
  echo "::error::usage: .wayfinder/ci-env.sh <preview|develop|prod>"
  exit 1
fi

# shellcheck source=ci.env disable=SC1091
source .wayfinder/ci.env

case "${stage}" in
  preview)
    if [[ -z "${PR_NUMBER:-}" ]]; then
      echo "::error::PR_NUMBER must be set for the preview stage."
      exit 1
    fi
    environment="${WF_ENV_PREVIEW}"
    account="${WF_SA_PREVIEW}"
    instance="${WF_SERVICE_NAME}-pr${PR_NUMBER}"
    ;;
  develop)
    environment="${WF_ENV_DEVELOP}"
    account="${WF_SA_DEVELOP}"
    instance="${WF_INSTANCE_DEVELOP}"
    ;;
  prod)
    environment="${WF_ENV_PROD}"
    account="${WF_SA_PROD}"
    instance="${WF_INSTANCE_PROD}"
    ;;
  *)
    echo "::error::Unknown stage '${stage}'. Expected preview, develop or prod."
    exit 1
    ;;
esac

# The workspace is a scaffold-time input. Where it was left blank, fall back to
# the WF_WORKSPACE repository or organisation variable, which is the better
# place for it when one workspace serves many repositories.
workspace="${WF_WORKSPACE:-${WF_WORKSPACE_VAR:-}}"
if [[ -z "${workspace}" ]]; then
  echo "::error::No Wayfinder workspace. Set the WF_WORKSPACE repository or organisation variable."
  exit 1
fi

args_file="${RUNNER_TEMP:-/tmp}/wf-deploy-args"
: >"${args_file}"

# The identity Wayfinder opens the cloud account with. Wayfinder reads which
# account that identity reaches, so no separate --target is needed.
#
# The default is the name a Wayfinder account vend gives the identity it creates
# for an environment: `aws-<environment>`, scoped to that environment in this
# workspace. Set the WF_IDENTITY variable to name a different one — an account
# you brought yourself, or one identity shared by every environment.
identity="${WF_IDENTITY:-${workspace}/${environment}/aws-${environment}}"
printf '%s\n' "--identity" "${identity}" >>"${args_file}"

# AWS has no default region, so a deploy without this fails on the first
# resource rather than here.
if [[ -z "${WF_REGION:-}" ]]; then
  echo "::error::No region. Set the WF_REGION repository or organisation variable (e.g. eu-west-2)."
  exit 1
fi
printf '%s\n' "--region" "${WF_REGION}" >>"${args_file}"

# Only when a zone is set. Without one the web app answers on CloudFront's own
# *.cloudfront.net domain, which needs no Route53 zone and no certificate.
if [[ -n "${WF_DNS_ZONE:-}" ]]; then
  printf '%s\n' "--dns-zone" "${WF_DNS_ZONE}" >>"${args_file}"
fi

{
  # $GITHUB_ENV accepts KEY=value lines only, so the comments in ci.env are
  # filtered out rather than passed through.
  grep -E '^[A-Z][A-Z0-9_]*=' .wayfinder/ci.env
  echo "WF_INSTANCE=${instance}"
  echo "WF_DEPLOY_ARGS_FILE=${args_file}"
  echo "WAYFINDER_TENANT=${WF_TENANT}"
  echo "WAYFINDER_WORKSPACE=${workspace}"
  echo "WAYFINDER_ENVIRONMENT=${environment}"
  echo "WAYFINDER_SERVICE_ACCOUNT=${WF_TENANT}:${workspace}:${account}"
} >>"${GITHUB_ENV:?GITHUB_ENV is not set; this script is meant to run in GitHub Actions}"

echo "Stage ${stage}: instance ${instance} in ${WF_TENANT}:${workspace}:${environment}"
echo "Deploying as service account ${WF_TENANT}:${workspace}:${account}"
echo "Through identity ${identity} in ${WF_REGION}"

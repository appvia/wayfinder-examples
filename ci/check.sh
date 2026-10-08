#!/usr/bin/env bash
# Checks the examples against Wayfinder: a server dry run of each directory below, so the API
# validates every resource in full and nothing is created.
#
#   ci/check.sh
#
# Pull requests run this against the production Wayfinder, as the service account in
# ci/service-account.yaml: what a customer applies is checked by what they apply it to.
#
# The plans are tenant-scoped as published, but they are checked as plans in the CHECK_WORKSPACE
# workspace (default wfci): a plan can live at either scope and is validated the same way at both,
# and a workspace role is all the service account then needs.
#
# A directory belongs here once everything in it validates without anything it refers to having
# to exist first, and the service account's role covers creating it. Not yet listed:
#   workflows/incident-triage   names a GitHubOrg, a workspace and credentials that would need to
#                               exist in the checking tenant first, and kinds beyond the plans
#                               workspace.cataloguemanagement covers
#
# Extra wf arguments (for example `--profile prod`) go in WF_ARGS.
set -euo pipefail

cd "$(dirname "$0")/.."

workspace="${CHECK_WORKSPACE:-wfci}"
dirs=(
  quickstart/aws/plans
  quickstart/azure/plans
  quickstart/azure-localaction/plans
)

# shellcheck disable=SC2206 # WF_ARGS is deliberately word-split
extra=(${WF_ARGS:-})

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

status=0
for dir in "${dirs[@]}"; do
  echo "==> ${dir}"
  copy="${work}/${dir}"
  mkdir -p "${copy}"
  for file in "${dir}"/*.yaml; do
    awk -v ws="${workspace}" '{ print } /^metadata:$/ { print "  workspace: " ws }' "${file}" > "${copy}/$(basename "${file}")"
  done
  if ! wf ${extra[@]+"${extra[@]}"} apply --dry-run server --file "${copy}"; then
    status=1
  fi
done
exit "${status}"

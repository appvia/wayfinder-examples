#!/usr/bin/env bash
# Checks the examples against Wayfinder: a server dry run of each directory below, so the API
# validates every resource in full and nothing is created.
#
#   WAYFINDER_WORKSPACE=<workspace> ci/check.sh
#
# The plans are checked as plans in WAYFINDER_WORKSPACE: a plan validates the same way at tenant
# and workspace scope.
#
# Extra wf arguments (for example `--profile prod`) go in WF_ARGS.
set -euo pipefail

cd "$(dirname "$0")/.."

workspace="${WAYFINDER_WORKSPACE:?set WAYFINDER_WORKSPACE to the workspace to check the plans in}"
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

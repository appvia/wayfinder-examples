#!/usr/bin/env bash
#
# Puts the `wf` CLI on the PATH of a GitHub-hosted runner.
#
#   usage: .wayfinder/install-wf.sh
#
# WF_CLI_VERSION in .wayfinder/ci.env picks the build. Leave it empty and CI
# tracks the latest development build, which is right while the platform itself
# is moving and wrong once it is not — pin a released version there when your
# deployments start mattering.
#
# The jobs run on a plain GitHub-hosted runner rather than in the wftoolbox
# container image, which keeps every job in this repository on one setup: the
# lint job needs make and ruby, which the runner already has.
set -euo pipefail

# shellcheck source=ci.env disable=SC1091
source .wayfinder/ci.env

version="${WF_CLI_VERSION:-}"
if [[ -n "${version}" ]]; then
  base="https://storage.googleapis.com/wayfinder-releases/${version}"
else
  base="https://storage.googleapis.com/wayfinder-dev-releases/latest"
fi

os="$(uname -s | tr '[:upper:]' '[:lower:]')"
arch="$(uname -m | sed 's/x86_64/amd64/')"

curl -fsSL "${base}/wf-cli-${os}-${arch}.tar.gz" -o /tmp/wf.tgz
tar -xzf /tmp/wf.tgz -C /tmp
sudo install -m 0755 "/tmp/wf-cli-${os}-${arch}" /usr/local/bin/wf
rm -f /tmp/wf.tgz

wf version

#!/usr/bin/env bash
#
# Puts one release's web app in the bucket CloudFront serves.
#
#   usage: .wayfinder/deploy-web.sh <bucket> <distribution-id> <release> <region>
#
# The stack's `deployweb` component calls this. Run it by hand from the
# repository root with credentials for the target account if you want to see
# what it would do.
#
# The bucket carries a `.release-<release>` marker object saying which release
# is in it, so:
#
#   Deploying the release that is already there does nothing at all. No npm, no
#   build, no invalidation.
#
#   Set SOURCE_BUCKET to another instance's bucket and this copies that build
#   across instead of making one. That is how a machine with no node — the
#   container an agent works in — deploys a release someone else built. Reading
#   the other bucket is the deployment identity's own access, so an identity
#   scoped to one instance's bucket is refused.
#
# Otherwise it builds the web app here and uploads it.
set -euo pipefail

bucket="${1:?usage: deploy-web.sh <bucket> <distribution-id> <release> <region>}"
distribution="${2:?usage: deploy-web.sh <bucket> <distribution-id> <release> <region>}"
release="${3:?usage: deploy-web.sh <bucket> <distribution-id> <release> <region>}"
export AWS_REGION="${4:?usage: deploy-web.sh <bucket> <distribution-id> <release> <region>}"

marker=".release-${release}"

if aws s3api head-object --bucket "${bucket}" --key "${marker}" >/dev/null 2>&1; then
  echo "deploy-web: ${bucket} already holds ${release}, so there is nothing to upload"
  exit 0
fi

if [[ -n "${SOURCE_BUCKET:-}" ]]; then
  echo "deploy-web: copying ${release} from ${SOURCE_BUCKET} into ${bucket}"
  # Nothing is excluded: the source's own marker names this same release, so
  # copying it is right.
  aws s3 sync "s3://${SOURCE_BUCKET}/" "s3://${bucket}/" --delete --sse
else
  echo "deploy-web: building ${release} and uploading it to ${bucket}"
  make web
  aws s3 sync web/dist "s3://${bucket}/" --delete --sse
fi

aws s3api put-object \
  --bucket "${bucket}" \
  --key "${marker}" \
  --server-side-encryption AES256 >/dev/null

aws cloudfront create-invalidation \
  --distribution-id "${distribution}" \
  --paths "/*" >/dev/null

echo "deploy-web: ${release} is live and CloudFront has been asked to forget the last one"

#!/usr/bin/env bash
#
# Puts one release's container image in ECR, doing as little as it can to get
# there.
#
#   usage: .wayfinder/ensure-image.sh <repository> <repository-uri> <release> <region>
#
# The stack's `pushimage` component calls this. Run it by hand from the
# repository root with credentials for the target account if you want to see
# what it would do.
#
# Two things it avoids:
#
#   A deploy that has nothing to build. When the tag is already in ECR this
#   exits before docker is mentioned, so a machine with no docker — the
#   container an agent works in — can deploy a release someone else built. Set
#   FORCE_BUILD to any value to build and push over the tag anyway, which is
#   what you want while iterating by hand on one RELEASE.
#
#   A first deploy that fails because the repository is not there yet. The ECR
#   registry is per account and always exists; the repository is created here,
#   once, named after the service.
set -euo pipefail

repository="${1:?usage: ensure-image.sh <repository> <repository-uri> <release> <region>}"
uri="${2:?usage: ensure-image.sh <repository> <repository-uri> <release> <region>}"
release="${3:?usage: ensure-image.sh <repository> <repository-uri> <release> <region>}"
export AWS_REGION="${4:?usage: ensure-image.sh <repository> <repository-uri> <release> <region>}"

# Lambda pulls the image as a service rather than as the caller, so it is
# refused without this even when the function and the repository are in one
# account.
lambda_pull_policy='{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "LambdaECRImageRetrievalPolicy",
      "Effect": "Allow",
      "Principal": { "Service": "lambda.amazonaws.com" },
      "Action": [
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer",
        "ecr:GetAuthorizationToken"
      ]
    }
  ]
}'

if ! aws ecr describe-repositories --repository-names "${repository}" >/dev/null 2>&1; then
  echo "ensure-image: creating the ${repository} repository"

  # Two pull requests opening at once both reach here on a service's first
  # deploy. The loser's create is refused with RepositoryAlreadyExistsException,
  # which says the repository is there — the thing that was wanted.
  created_errors="$(mktemp)"
  trap 'rm -f "${created_errors}"' EXIT
  if ! aws ecr create-repository \
    --repository-name "${repository}" \
    --image-scanning-configuration scanOnPush=true \
    --tags "Key=Application,Value=${repository}" "Key=ManagedBy,Value=Wayfinder" \
    >/dev/null 2>"${created_errors}"; then
    if ! grep -q RepositoryAlreadyExistsException "${created_errors}"; then
      cat "${created_errors}" >&2
      exit 1
    fi
    echo "ensure-image: another deploy created ${repository} first"
  fi

  aws ecr set-repository-policy \
    --repository-name "${repository}" \
    --policy-text "${lambda_pull_policy}" >/dev/null
fi

if [[ -n "${FORCE_BUILD:-}" ]]; then
  echo "ensure-image: FORCE_BUILD is set, building ${uri}:${release} over whatever is there"
elif aws ecr describe-images \
  --repository-name "${repository}" \
  --image-ids "imageTag=${release}" >/dev/null 2>&1; then
  echo "ensure-image: ${uri}:${release} is already in ECR, so there is nothing to build"
  exit 0
fi

echo "ensure-image: building and pushing ${uri}:${release}"

# A login is per registry host, so the repository path is trimmed off. `make
# image` tags the build after the service, which is also what the repository is
# called.
aws ecr get-login-password | docker login --username AWS --password-stdin "${uri%%/*}"
make image RELEASE="${release}"
docker tag "${repository}:${release}" "${uri}:${release}"
docker push "${uri}:${release}"

#!/usr/bin/env bash
#
# Puts one app's container image for one release in ECR, doing as little as it
# can to get there.
#
#   usage: .wayfinder/ensure-image.sh <app-dir> <repository> <repository-uri> <release> <region>
#
#   .wayfinder/ensure-image.sh message-service my-svc-message-service \
#     123456789012.dkr.ecr.eu-west-2.amazonaws.com/my-svc-message-service sha-1234567 eu-west-2
#
# The stack's `pushimages` component calls this once for each app. Run it by
# hand from the repository root with credentials for the target account if you
# want to see what it would do.
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
#   once, named after the service and the app.
#
# There is no repository policy. ECS pulls the image as the task execution role,
# which is the service's Wayfinder workload identity, and the
# aws-ecs-fargate-service plan already grants that role ECR pull in this account.
set -euo pipefail

usage="usage: ensure-image.sh <app-dir> <repository> <repository-uri> <release> <region>"
app="${1:?${usage}}"
repository="${2:?${usage}}"
uri="${3:?${usage}}"
release="${4:?${usage}}"
export AWS_REGION="${5:?${usage}}"

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
# image` tags the build <service>-<app>, which is also what the repository is
# called, so the local tag is <repository>:<release>.
aws ecr get-login-password | docker login --username AWS --password-stdin "${uri%%/*}"
make image APP="${app}" RELEASE="${release}"
docker tag "${repository}:${release}" "${uri}:${release}"
docker push "${uri}:${release}"

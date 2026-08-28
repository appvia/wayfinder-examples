#!/usr/bin/env bash
#
# Renders a template's skeleton with placeholder values so CI can compile it.
#
#   usage: repo-templates/hack/render-for-build.sh <template-dir> <output-dir>
#
# This is deliberately NOT a faithful implementation of Wayfinder's template
# engine. It substitutes the simple `${{ .Inputs.x }}` style references and
# renames templated directories, which is all that is needed to answer "does
# this skeleton actually build and pass its own tests". Conditionals appear only
# in Wayfinder.yaml, which is excluded here and checked properly by the render
# test in the wayfinder repository and by `wf create stack --dry-run`.
set -euo pipefail

template_dir="${1:?usage: render-for-build.sh <template-dir> <output-dir>}"
out_dir="${2:?usage: render-for-build.sh <template-dir> <output-dir>}"

skeleton="${template_dir}/skeleton"
if [[ ! -d "${skeleton}" ]]; then
  echo "no skeleton in ${template_dir}" >&2
  exit 1
fi

rm -rf "${out_dir}"
mkdir -p "${out_dir}"
cp -R "${skeleton}/." "${out_dir}/"

# Wayfinder.yaml uses conditionals this script does not implement, and nothing
# in a language build reads it.
rm -f "${out_dir}/Wayfinder.yaml"

python3 - "${out_dir}" <<'PYTHON'
import os
import re
import sys

root = sys.argv[1]

# Placeholder values. Chosen to be valid everywhere: a Go package name, a Java
# artifact id, a Python module and an npm package name all accept these.
VALUES = {
    ".Inputs.serviceName": "example",
    ".Inputs.description": "An example service",
    ".Inputs.owner": "example-team",
    ".Inputs.workspace": "example-workspace",
    ".Inputs.developEnvironment": "dev",
    ".Inputs.prodEnvironment": "prod",
    ".Inputs.previewEnvironment": "dev",
    ".Inputs.registry": "ghcr.io",
    ".Inputs.port": "8080",
    ".Inputs.includeStorage": "true",
    ".Inputs.storagePlan": "file:plans/aws-s3-data-bucket.yaml",
    ".Inputs.gatewayName": "",
    ".Inputs.gatewayNamespace": "",
    ".Inputs.groupId": "com.example",
    ".Inputs.groupPath": "com/example",
    ".Repo.Organization": "example-org",
    ".Repo.Name": "example-service",
    ".Repo.URL": "https://github.com/example-org/example-service",
    ".Repo.DefaultBranch": "main",
    ".Stack.Name": "example",
    ".Tenant": "example-tenant",
}

EXPRESSION = re.compile(r"\$\{\{-?\s*(\.[A-Za-z0-9_.]+)\s*-?\}\}")

# Files copied verbatim by the real engine. Substituting in them here would be
# wrong, and in the workflows it would eat GitHub Actions' own expressions.
def is_raw(rel):
    return (
        rel.startswith(".github/workflows/")
        or rel.startswith("charts/")
        or rel.startswith("plans/")
        or rel.endswith(".sh")
    )


def substitute(text):
    def replace(match):
        key = match.group(1)
        if key not in VALUES:
            raise SystemExit(f"render-for-build.sh does not know {key}; add it to VALUES")
        return VALUES[key]

    return EXPRESSION.sub(replace, text)


# Contents first.
for dirpath, _, filenames in os.walk(root):
    for filename in filenames:
        path = os.path.join(dirpath, filename)
        rel = os.path.relpath(path, root)
        if is_raw(rel):
            continue
        try:
            with open(path, encoding="utf-8") as handle:
                original = handle.read()
        except UnicodeDecodeError:
            continue  # binary, passed through
        rendered = substitute(original)
        if rendered != original:
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(rendered)

# Then paths, deepest first so renaming a parent cannot invalidate a child path.
for dirpath, dirnames, filenames in os.walk(root, topdown=False):
    for name in filenames + dirnames:
        if "${{" not in name:
            continue
        os.rename(os.path.join(dirpath, name), os.path.join(dirpath, substitute(name)))
PYTHON

echo "Rendered ${template_dir} into ${out_dir}"

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

python3 - "${out_dir}" "${template_dir}/wayfinder-template.yaml" <<'PYTHON'
import fnmatch
import os
import re
import sys

import yaml

root = sys.argv[1]
MANIFEST = sys.argv[2]

# Placeholder values for the render context.
#
# Input values come from the manifest itself — its declared default where there
# is one, otherwise something type-appropriate — so a template that adds an
# input does not have to be registered here as well. NAME_LIKE covers the inputs
# whose value ends up in a path or an identifier, where "example" is safe and a
# generic placeholder might not be.
NAME_LIKE = {"serviceName", "packageName", "name", "appName", "moduleName"}

CONTEXT = {
    ".Repo.Organization": "example-org",
    ".Repo.Name": "example-service",
    ".Repo.URL": "https://github.com/example-org/example-service",
    ".Repo.DefaultBranch": "main",
    ".Stack.Name": "example",
    ".Tenant": "example-tenant",
}


def placeholder(definition):
    """A usable value for one declared input."""
    name = definition.get("name", "")
    if name in NAME_LIKE:
        return "example"
    if "defaultValue" in definition:
        value = definition["defaultValue"]
        return "true" if value is True else "false" if value is False else str(value)
    kind = definition.get("type", "string")
    if kind == "number":
        return "1"
    if kind == "bool":
        return "false"
    if definition.get("enum"):
        return str(definition["enum"][0])
    return "example"


def build_values(manifest):
    values = dict(CONTEXT)
    for definition in manifest.get("inputs", []) or []:
        values[".Inputs." + definition["name"]] = placeholder(definition)
    return values


EXPRESSION = re.compile(r"\$\{\{-?\s*(\.[A-Za-z0-9_.]+)\s*-?\}\}")

# Which files the real engine copies verbatim, read from the template's own
# skeleton.raw globs rather than assumed. The two conventions in this repository
# disagree about charts and workflows on purpose — one exempts them with a glob,
# the other escapes each expression and lets them render — so guessing here
# renders files that should not be, and skips files that should.
#
# Matched the way Wayfinder matches them: Go's path.Match, where `*` never
# crosses a `/` and there is no `**`. fnmatch's `*` DOES cross `/`, hence the
# segment-count guard.
def raw_matcher(globs):
    def is_raw(rel):
        for pattern in globs:
            if pattern.count("/") != rel.count("/"):
                continue
            if fnmatch.fnmatchcase(rel, pattern):
                return True
        return False

    return is_raw


with open(MANIFEST, encoding="utf-8") as handle:
    manifest = yaml.safe_load(handle)
is_raw = raw_matcher((manifest.get("skeleton", {}) or {}).get("raw", []) or [])
VALUES = build_values(manifest)


def substitute(text):
    def replace(match):
        key = match.group(1)
        if key not in VALUES:
            raise SystemExit(
                f"render-for-build.sh cannot resolve the expression for {key}: it is not a declared "
                "input of this template, and not part of the render context"
            )
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

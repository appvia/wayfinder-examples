#!/usr/bin/env python3
"""Structural checks for the repo templates.

Everything here can be answered without a Wayfinder server: it checks the things
that are true of a template by construction. The parts that need the real
template engine — that a skeleton renders at all, and that Wayfinder.yaml comes
out as a readable stack — are covered by the render test in the wayfinder
repository and by `wf create stack --dry-run`.

    usage: repo-templates/hack/check-templates.py [repo-templates-dir]

Two groups of checks:

  every template   manifest parses, ships a README, ships no wayfinder-stack.yaml,
                   respects the fetch limits, and leaves no foreign ${{ }}
                   expression bare in a file Wayfinder will render.

  the pipeline set the *-app family (go, python, node, java, dotnet), which share one CI
                   contract and one inputs contract. Mark's go-service,
                   go-microservice and go-library deliberately differ from each
                   other — they exist to demonstrate different mechanics — so the
                   family checks do not apply to them.
"""

from __future__ import annotations

import fnmatch
import os
import re
import pathlib
import sys

import yaml

# Wayfinder refuses a template tree that exceeds these.
MAX_FILE_SIZE = 1024 * 1024
MAX_FILES = 2000

# Templates that share the CI and inputs contract.
PIPELINE_TEMPLATES = ["go-app", "python-app", "node-app", "java-app", "dotnet-app"]

# The inputs every template in the pipeline family declares, in this order.
SHARED_INPUTS = [
    "serviceName",
    "description",
    "owner",
    "workspace",
    "developEnvironment",
    "prodEnvironment",
    "previewEnvironment",
    "registry",
    "port",
    "includeStorage",
    "storagePlan",
    "gatewayName",
    "gatewayNamespace",
]

# Files whose ${{ }} expressions are not all Wayfinder's. A GitHub Actions
# workflow, a Helm chart and a CloudResourcePlan each have their own templating
# in the same delimiters, and there are two valid ways to protect it: exempt the
# file with a skeleton.raw glob, or escape each foreign expression as `$${{`.
# Both are in use here deliberately. What is never valid is leaving a foreign
# expression bare in a rendered file — Wayfinder evaluates it at scaffold time
# and either fails outright or silently eats it.
def has_foreign_expressions(rel: str) -> bool:
    return (
        rel.startswith(".github/")
        or rel.startswith("charts/")
        or rel.startswith("plans/")
        or rel.endswith(".sh")
    )


# The whole render context. Anything else inside a bare ${{ }} belongs to
# something other than the scaffolder.
SCAFFOLD_ROOTS = (".Inputs", ".Repo", ".Stack", ".Tenant")

# Control structures and pipeline helpers, which carry no context of their own.
SCAFFOLD_KEYWORDS = ("if", "end", "else", "range", "with", "define", "template", "block")

# A ${{ ... }} not already escaped as $${{ ... }}.
BARE_EXPRESSION = re.compile(r"(?<!\$)\$\{\{(.*?)\}\}", re.DOTALL)


def foreign_expressions(content: str) -> list[str]:
    """Bare ${{ }} expressions that the scaffolder cannot resolve."""
    found = []
    for expression in BARE_EXPRESSION.findall(content):
        body = expression.strip().lstrip("-").strip()
        if not body:
            continue
        first = body.split()[0].lstrip("-")
        if first in SCAFFOLD_KEYWORDS:
            continue
        # A reference the scaffolder owns, anywhere in the expression, makes it
        # a scaffold expression — `.Inputs.x | default "y"` is still ours.
        if any(root in body for root in SCAFFOLD_ROOTS):
            continue
        found.append(body)
    return found


class Failures:
    def __init__(self) -> None:
        self.items: list[str] = []

    def add(self, where: str, message: str) -> None:
        self.items.append(f"{where}: {message}")
        # GitHub renders this as an annotation on the run.
        print(f"::error file={where}::{message}")

    def ok(self) -> bool:
        return not self.items


def matches_raw(globs: list[str], rel: str) -> bool:
    # Wayfinder matches these with Go's path.Match: no `**`, and a `*` never
    # crosses a `/`. fnmatch's `*` DOES cross `/`, so compare segment counts too
    # or this check would pass globs Wayfinder will not honour.
    for pattern in globs:
        if pattern.count("/") != rel.count("/"):
            continue
        if fnmatch.fnmatchcase(rel, pattern):
            return True
    return False


def skeleton_files(skeleton: pathlib.Path) -> list[tuple[str, pathlib.Path]]:
    found = []
    for dirpath, _, filenames in os.walk(skeleton):
        for filename in filenames:
            path = pathlib.Path(dirpath) / filename
            found.append((str(path.relative_to(skeleton)), path))
    return sorted(found)


def check_template(root: pathlib.Path, name: str, fail: Failures) -> dict | None:
    directory = root / name
    manifest_path = directory / "wayfinder-template.yaml"
    where = f"repo-templates/{name}/wayfinder-template.yaml"

    try:
        manifest = yaml.safe_load(manifest_path.read_text())
    except FileNotFoundError:
        fail.add(where, "no wayfinder-template.yaml")
        return None
    except yaml.YAMLError as err:
        fail.add(where, f"is not valid YAML: {err}")
        return None

    if not manifest.get("name"):
        fail.add(where, "declares no name")

    skeleton_dir = manifest.get("skeleton", {}).get("path", "skeleton")
    skeleton = directory / skeleton_dir
    if not skeleton.is_dir():
        fail.add(where, f"skeleton.path {skeleton_dir!r} is not a directory")
        return manifest

    raw_globs = manifest.get("skeleton", {}).get("raw", []) or []
    files = skeleton_files(skeleton)

    if len(files) > MAX_FILES:
        fail.add(where, f"{len(files)} files, more than the {MAX_FILES} Wayfinder will fetch")

    has_readme = False
    for rel, path in files:
        location = f"repo-templates/{name}/{skeleton_dir}/{rel}"

        if path.is_symlink():
            fail.add(location, "is a symlink; Wayfinder skips those, so it would silently vanish")
            continue

        size = path.stat().st_size
        if size > MAX_FILE_SIZE:
            fail.add(location, f"is {size} bytes, over the {MAX_FILE_SIZE} byte limit")

        if rel == "README.md":
            has_readme = True
        if rel == "wayfinder-stack.yaml":
            fail.add(location, "is written by Wayfinder at scaffold time; shipping one gets it overwritten")

        is_raw = matches_raw(raw_globs, rel)
        try:
            content = path.read_text()
        except UnicodeDecodeError:
            continue  # binary, passed through whichever way

        if is_raw:
            if "$${{" in content:
                fail.add(location, "is raw, so `$${{` is not an escape here — it lands literally")
        elif has_foreign_expressions(rel):
            for expression in foreign_expressions(content):
                fail.add(
                    location,
                    f"is rendered, but `${{{{ {expression} }}}}` is not a scaffold expression. "
                    "Escape it as `$${{ ... }}`, or exempt the file with a skeleton.raw glob "
                    "(path.Match has no `**`, and `*.yaml` does not match `*.yml`)",
                )

    if not has_readme:
        fail.add(
            f"repo-templates/{name}/{skeleton_dir}",
            "ships no README.md, so the repository Wayfinder creates would have none "
            "(the auto-init stub is deleted)",
        )

    return manifest


def check_pipeline_family(root: pathlib.Path, manifests: dict[str, dict], fail: Failures) -> None:
    shared = root / "_shared" / "workflows"
    if not shared.is_dir():
        fail.add("repo-templates/_shared/workflows", "does not exist")
        return

    for workflow in sorted(shared.iterdir()):
        want = workflow.read_bytes()
        for name in PIPELINE_TEMPLATES:
            copy = root / name / "skeleton" / ".github" / "workflows" / workflow.name
            location = f"repo-templates/{name}/skeleton/.github/workflows/{workflow.name}"
            if not copy.exists():
                fail.add(location, f"is missing; copy it from _shared/workflows/{workflow.name}")
            elif copy.read_bytes() != want:
                fail.add(location, f"has drifted from _shared/workflows/{workflow.name}; copy the shared file over it")

    for name in PIPELINE_TEMPLATES:
        manifest = manifests.get(name)
        if not manifest:
            continue
        declared = [i.get("name") for i in manifest.get("inputs", []) or []]
        where = f"repo-templates/{name}/wayfinder-template.yaml"
        missing = [i for i in SHARED_INPUTS if i not in declared]
        if missing:
            fail.add(where, f"is missing shared inputs: {', '.join(missing)}")
        for definition in manifest.get("inputs", []) or []:
            if definition.get("name") != "serviceName":
                continue
            if definition.get("optional"):
                fail.add(where, "serviceName must be required")
        for definition in manifest.get("inputs", []) or []:
            n = definition.get("name")
            if n in SHARED_INPUTS and n != "serviceName":
                if not definition.get("optional"):
                    fail.add(where, f"input {n!r} must be optional, or a --no-input scaffold cannot work")
                elif "defaultValue" not in definition:
                    fail.add(where, f"input {n!r} is optional but declares no default")


def check_registrations(root: pathlib.Path, fail: Failures) -> None:
    for registration in sorted(root.glob("*/RepoTemplate-*.yaml")):
        where = str(registration.relative_to(root.parent))
        try:
            doc = yaml.safe_load(registration.read_text())
        except yaml.YAMLError as err:
            fail.add(where, f"is not valid YAML: {err}")
            continue
        spec = doc.get("spec", {})
        path = spec.get("path")
        if not path:
            fail.add(where, "declares no spec.path")
            continue
        target = root.parent / path / "wayfinder-template.yaml"
        if not target.exists():
            fail.add(where, f"spec.path {path!r} has no wayfinder-template.yaml")
        expected = f"repo-templates/{registration.parent.name}"
        if path != expected:
            fail.add(where, f"spec.path is {path!r} but the file lives in {expected!r}")


def main() -> int:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "repo-templates")
    if not root.is_dir():
        print(f"no such directory: {root}", file=sys.stderr)
        return 2

    fail = Failures()
    manifests: dict[str, dict] = {}

    for directory in sorted(root.iterdir()):
        if not directory.is_dir() or directory.name.startswith("_") or directory.name == "hack":
            continue
        manifest = check_template(root, directory.name, fail)
        if manifest:
            manifests[directory.name] = manifest

    check_pipeline_family(root, manifests, fail)
    check_registrations(root, fail)

    if not fail.ok():
        print(f"\n{len(fail.items)} problem(s) found.", file=sys.stderr)
        return 1

    print(f"Checked {len(manifests)} templates: all good.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

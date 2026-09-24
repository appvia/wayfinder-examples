# CI calls `make lint` and nothing else. There is no application code here, so
# there is nothing to build or unit test: what can be wrong before a deploy is a
# file that is not valid YAML, or a Wayfinder.yaml naming a plan file that is
# not in plans/. The server-side check is `wf deploy --dry-run`, which the pull
# request workflow runs.

YAML_FILES := Wayfinder.yaml $(wildcard plans/*.yaml) $(wildcard .github/workflows/*.yaml)

.PHONY: lint
lint:
	@set -eu; \
	for f in $(YAML_FILES); do \
		ruby -ryaml -e 'YAML.load_file(ARGV[0])' "$$f" || { echo "lint: $$f is not valid YAML" >&2; exit 1; }; \
	done; \
	for plan in $$(sed -n 's/^ *plan: *file:\(.*\)$$/\1/p' Wayfinder.yaml); do \
		[ -f "$$plan" ] || { echo "lint: Wayfinder.yaml deploys $$plan, which does not exist" >&2; exit 1; }; \
	done; \
	echo "lint: $(words $(YAML_FILES)) YAML files parse, and every file: plan in Wayfinder.yaml exists"

This repository provides a set of examples to get started using [Wayfinder](https://on.wayfinder.run/).

To use these examples, clone the repository, amend the configurations as you wish, then apply:

```
$ git clone https://github.com/appvia/wayfinder-examples.git
$ cd wayfinder-examples

# Apply files or folders of your choosing to your Wayfinder tenant, for example, add the example quickstart plans on AWS:
$ wf apply -f ./quickstart/aws/plans

# Build the example stack:
$ cd ./quickstart/aws/
$ wf up
```

For full documentation, see the [quickstart readme](./quickstart/README.md) or [https://on.wayfinder.run/docs/getting-started/01-quick-start](https://on.wayfinder.run/docs/getting-started/01-quick-start).

## What's here

| Example | What it shows |
| ------- | ------------- |
| [`quickstart/`](./quickstart/README.md) | Deploy your first application: example plans plus a `Wayfinder.yaml` for AWS or Azure |
| [`workflows/incident-triage/`](./workflows/incident-triage/README.md) | Integrations and workflows end to end: a monitoring alert arrives on an inbound webhook, an AI agent investigates it autonomously, and severity-gated tasks open a GitHub issue and page Slack |
| [`repo-templates/`](./repo-templates/README.md) | Repository templates: register one, then create a new service repository from it with `wf create stack --from-template` |
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
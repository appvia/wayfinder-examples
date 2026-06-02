This repository provides a set of examples to get started using Wayfinder.

To use these examples, clone the repository, amend the configurations as you wish, then apply:

```
$ git clone https://github.com/appvia/wayfinder-examples.git
$ cd wayfinder-examples

# Apply files or folders of your choosing to your Wayfinder tenant, for example, add the example quickstart plans:
$ wf apply -f ./quickstart/plans

# Build the example stack:
$ cd ./quickstart/
$ wf up
```

For full documentation, see the [quickstart readme](./quickstart/README.md) or [https://on.wayfinder.run/docs/getting-started/01-quick-start](https://on.wayfinder.run/docs/getting-started/01-quick-start).
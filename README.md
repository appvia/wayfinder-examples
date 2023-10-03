<p align="center">
    <img alt="Wayfinder logo" title="Wayfinder" src="wayfinder-logo-color-horizontal.svg">
</p>

This repository provides an example set of configuration to get started using Wayfinder.

To use these examples, clone the repository, amend the configurations as you wish, then apply:

```
$ git clone https://github.com/appvia/wayfinder-examples.git
$ cd wayfinder-examples

# Apply files or folders of your choosing to your Wayfinder instance, for example, add all of the
# example network and cluster plans:
$ wf apply -f ./plateform/assignablenetworks
$ wf apply -f ./plateform/clusternetworkplans
$ wf apply -f ./plateform/clusterplans
```

You can also copy these definitions into your repository as a starting point for your own
configuration.
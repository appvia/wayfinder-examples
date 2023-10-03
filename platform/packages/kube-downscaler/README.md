# Wayfinder Package: KEDA Operator

This package deploys the [splunk helm chat](https://github.com/splunk/splunk-connect-for-kubernetes) across all Clusters.

## Deployment 
Deployment is handled by the workflow.

## Configuration

When making changes to the configuration, you will need update the package version commit your chagnes to main which will trgucer the workflow.
after a new version of the package is added to wayfinder you can upgrade each cluster. documentation on how to upgrade can be found [here](https://docs.appvia.io/wayfinder/using/upgrades/package-upgrades "Wayfinder Package Upgrades")

## Secrets

The secret was created manually using the following command.

> wf -w admin create secret generic splunk -d "My Splunk token" --from-literal=token={{Token}}

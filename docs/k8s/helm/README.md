# Zalenium

## TL;DR;

```console
$ helm install local/zalenium
```

## Introduction

This chart bootstraps a [Zalenium](https://github.com/zalando/zalenium) deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager.

## Prerequisites

- Kubernetes 1.5+ with Beta APIs enabled

## Installing the Chart

To install the chart with the release name `my-release`:

```console
$ helm install --name my-release local/zalenium
```

The command deploys Zalenium on the Kubernetes cluster in the default configuration. The [configuration](#configuration) section lists the parameters that can be configured during installation.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete the `my-release` deployment:

```console
$ helm delete my-release
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Configuration

The following tables lists the configurable parameters of the Selenium chart and their default values.

See Zalenium's [usage examples](https://github.com/zalando/zalenium/blob/master/docs/usage_examples.md) for better descriptions of parameters, below.

| Parameter | Description | Default |
| --------- | ----------- | ------- |
| `persistence` | Whether to use a persistent volume claim for storage. Set persistence.enabled to true | false |
| `rbac` | If your cluster has RBAC enabled, you can choose to either have the chart create its own service account or provide one.To have the chart create the service account for you, set rbac.create to true | false |
| `serviceAccount` | Set serviceAccount.create to true if you want to create an service account for zalenium | false |
| `ingress` | Set ingress.enabled to true if you want to create an ingress entry for zalenium | false |
| `nodeSelector` | Configure node selector if you want to run zalenium on specified nodes | false |
| `tolerations` | Configure tolerations if you want to run zalenium on nodes with taints | false |
| `hub.image` | The zalenium hub image | `dosel/zalenium` |
| `hub.tag` | The zalenium hub image tag | `3` |
| `hub.pullPolicy` | The pull policy for the hub image | `IfNotPresent` |
| `hub.port` | The port the hub listens on | `4444` |
| `hub.readinessTimeout` | Timeout for probe Hub readiness via HTTP request on Hub console | `1` |
| `hub.javaOpts` | The java options for the selenium hub JVM, default sets the maximum heap size to 400 mb | `-Xmx400m -XX:+UseSerialGC` |
| `hub.localVolumesRoot` | The root directory to store HostPath volumes (e.g. if running in minikube) | `/tmp` |
| `hub.resources` | The resources for the hub container, defaults to minimum half a cpu and maximum 512 mb RAM | `{"limits":{"cpu":".5", "memory":"512Mi"}}` |
| `hub.serviceType` | The Service type | `NodePort` |
| `hub.serviceSourceRanges` | The list of IPs allowed to connect to the service. Important - in command line you have to escape commas, e.g.: `{"10.10.0.0/24\,10.20.0.0/16"}` | `{"0.0.0.0/0"}` |
| `hub.serviceSessionAffinity` | The session affinity for the hub service| `None` |
| `hub.desiredContainers` | How many pods to launch at start | 2 |
| `hub.maxDockerSeleniumContainers` | Maximum number of Selenium containers to run simultaneously | 10 |
| `hub.sauceLabsEnabled` | Enable SauceLabs | false |
| `hub.browserStackEnabled` | Enable BrowserStack | false |
| `hub.testingBotEnabled` | Enable TestingBot | false |
| `hub.videoRecordingEnabled` | Enable video recording | true |
| `hub.cpuRequest` | CPU requested for browser pods.  The hub passes this value to the k8s API | 500m |
| `hub.cpuLimit` | CPU limit for browser pods.  The hub passes this value to the k8s API | 1000m |
| `hub.memRequest` | Memory requested for browser pods.  The hub passes this value to the k8s API | 500Mi |
| `hub.screenWidth` | Screen resolution to use | 1440 |
| `hub.screenHeight` | Screen resolution to use | 900 |
| `hub.timeZone` | Time zone | UTC |
| `hub.seleniumImageName` | The Selenium grid image | `elgalu/selenium` |
| `hub.maxTestSessions` | The number of tests to run on each grid container before killing it and starting a new one | 1 |
| `hub.debugEnabled` | 	Enables LogLevel.FINE | false |
| `hub.keepOnlyFailedTests` | Keeps only failed tests on the dashboard (you need to send a cookie with the test result) | false |
| `hub.retentionPeriod` | Number of day's a testentry should be kept in dashboard before cleanup | 3 |
| `hub.sendAnonymousUsageInfo` | Allows sending anonymous usage info | true |
| `hub.sauceUserName` | Username to log into saucelabs | blank |
| `hub.sauceAccessKey` | Access key to log into saucelabs | blank |
| `hub.browserStackUser` | Credentials for browserstack | blank |
| `hub.browserStackKey` | Credentials for browserstack | blank |
| `hub.testingBotKey` | Credentials for testingbot | blank |
| `hub.testingBotSecret` | Credentials for testingbot | blank |
| `hub.basicAuth.enabled` | Enables basic authentication | false |
| `hub.basicAuth.username` | Username for basic authentication | zalenium |
| `hub.basicAuth.password` | Password for basic authentication | password |

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`. For example,

```console
$ helm install --name my-release \
  --set hub.gridTimeout=300 \
    local/zalenium
```

Alternatively, a YAML file that specifies the values for the above parameters can be provided while installing the chart. For example,

```console
$ helm install --name my-release -f values.yaml local/zalenium
```

> **Tip**: You can use the default [values.yaml](local/zalenium/values.yaml)

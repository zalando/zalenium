# Kubernetes Support

Zalenium has support for [Kubernetes](https://kubernetes.io/), these instructions will give you an
overview of how to get it running. Be aware that this is work in progress and some things could be missing, both in
code and documentation.

Zalenium integrates with Kubernetes using the
[fabric8 kubernetes-client and openshift-client](https://github.com/fabric8io/kubernetes-client/)
and the initial support was developed on OpenShift, but should be backwards compatible with vanilla Kubernetes and
has been tested on [minikube](https://github.com/kubernetes/minikube). If you have Google Container Engine (GKE), you can
complement this guide with this [document](./gke/gke.md).

## Helm
[Helm](https://helm.sh) is a tool that greatly simplifies installing apps on a Kubernetes cluster.  Helm users can 
see an example of a Helm chart that installs Zalenium grid in the [docs/k8s/helm](helm/README.md) 
directory.  Support can be added for different storage classes, RBAC support (and/or OpenShift).

## Service Account
Zalenium uses a service account that is automatically mounted by Kubernetes, it uses this service account to create
selenium pods and their related services.

It is a good idea to create a separate service account for specific use by Zalenium, especially when running inside
OpenShift because it uses role based authentication by default, meaning that the service account will need a `ClusterRole`
created that has the necessary privileges to access the parts of the Kubernetes API that it needs to.

### Kubernetes Service Account
Create the zalenium service account, this should be enough for minikube.
```sh
kubectl create sa zalenium
```

Starting from Kubernetes 1.6, there is beta [RBAC support](http://blog.kubernetes.io/2017/04/rbac-support-in-kubernetes.html)
(Role Based Access Control), it is possible that this may be similar to the built-in RBAC support in OpenShift.
If you want to use RBAC support in Kubernetes, you could try adapting the OpenShift instructions below.

### OpenShift Service Account
First up, create the [cluster role](./zalenium-role.json):
```sh
oc create -f ./zalenium-role.json
```

Then create the zalenium service account:

```sh
oc create sa zalenium
```

Then allow the zalenium service account to run as any user, as Zalenium presently needs to run as root, which OpenShift
doesn't allow by default.

```sh
oc adm policy add-scc-to-user anyuid -z zalenium
```

Next add the `zalenium-role` you just created to the zalenium service account.
```sh
oc adm policy add-role-to-user zalenium-role -z zalenium
```

In case you get a message like:
```sh
Error from server (NotFound): role.authorization.openshift.io "exampleview" not found
```

Check the namespace where you deployment is and add it to the previous command, e.g.:
```sh
# Check namespaces
oc get namespace
# Execute command
Error from server (NotFound): role.authorization.openshift.io "exampleview" not found
oc adm policy add-role-to-user zalenium-role -z zalenium --role-namespace='your_deployment_namespace'
```

## App label
Zalenium relies on there being an `app=<something>` label that it will use to locate `Services` and during `Pod` creation.
This means that you can have multiple zalenium deployments in the same kubernetes namespace that can operate independently
if they have different app labels.

A good default to use would be: `app=zalenium`.

## Overriding the Selenium Image
For performance reasons it could be a good idea to pull the selenium image, `elgalu/selenium`, into a local registry,
especially since the image will need to be available on potentially any kubernetes node.

In Openshift there is a built in registry that can automatically pull the an image from an external registry
(such as docker hub)
[on a schedule](https://docs.openshift.com/container-platform/3.5/dev_guide/managing_images.html#importing-tag-and-image-metadata).

This command will automatically import `elgalu/selenium` into the OpenShift registry at `delivery/selenium:latest`
updating it on a schedule.

```sh
oc tag docker.io/elgalu/selenium:latest delivery/selenium:latest --scheduled=true
```

This would then be available at `172.23.192.79:5000/delivery/selenium:latest` in the OpenShift registry for example.

To use that image, specify `--seleniumImageName 172.23.192.79:5000/delivery/selenium:latest` when starting zalenium.

Note: It looks like the OpenShift Image Scheduler in not enabled by default, have a read of
[this issue](https://github.com/openshift/origin/issues/9037)
to see how to enable it.

## Auto-mounting the shared folder
Like the Docker version of Zalenium, the Kubernetes version can automatically mount the shared folder, as long as it is
mounted at `/tmp/mounted` the only catch is that when you are using persistent volumes you need to make sure that the
`Access Mode` is set to `ReadWriteMany`, otherwise the selenium nodes will not be able to mount it.

So for example you could create a persistent volume with these contents:

```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: zalenium-shared
spec:
  accessModes:
    - ReadWriteMany
  capacity:
    storage: 5Gi
  hostPath:
    path: /data/zalenium-shared/
```

And a claim like this:

```yaml
kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: zalenium-shared
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 5Gi
```

Zalenium will scan the `volumeMounts` for the zalenium container when it starts up, if it finds a matching volume mount
at `/tmp/mounted` it will copy the `volume mount` information and the linked `volume` information when it creates a
selenium pod.

## Managing Resources
Kubernetes has [support](https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/) for managing how
much resources a Pod is allowed to use.  Especially when using video recording it is highly recommended to specify some resource
requests and/or
limits otherwise users of your Kubernetes cluster may be negatively affected by the selenium pods.

There are 2 resource requests and 2 resource limits that you can set.  The following table lists the possible values that you can use,
however, there are no defaults, so if you don't specify anything, no resource limits or requests will be set.

|Name          |Environment Variable                |Example                                                                                          |
|:-------------|:-----------------------------------|:------------------------------------------------------------------------------------------------|
|CPU Request   |`ZALENIUM_KUBERNETES_CPU_REQUEST`   |`250m` (25% of a CPU core)                                                                       |
|CPU Limit     |`ZALENIUM_KUBERNETES_CPU_LIMIT`     |`500m` (50% of a CPU core)                                                                       |
|Memory Request|`ZALENIUM_KUBERNETES_MEMORY_REQUEST`|`1Gi` (1 Gibibyte)                                                                               |
|Memory Limit  |`ZALENIUM_KUBERNETES_MEMORY_LIMIT`  |Probably best to leave empty, because Kubernetes will kill the container if it exceeds the value.|

## Starting with Kubernetes
Create the deployment

```sh
kubectl run zalenium \
    --image=dosel/zalenium \
    --overrides='{"spec": {"template": {"spec": {"serviceAccount": "zalenium"}}}}' \
    -l app=zalenium,role=grid \
    -- start --desiredContainers 2
```

Create the services

```sh
kubectl create service nodeport zalenium-grid --tcp=4444:4444 --dry-run -o yaml \
    | kubectl label --local -f - app=zalenium --overwrite -o yaml \
    | kubectl set selector --local -f - app=zalenium,role=grid -o yaml \
    | grep -v "running in local/dry-run mode" \
    | kubectl create -f -

```

Then you can open the grid in minikube by running

```sh
minikube service zalenium-grid
```

For videos to work you need to mount in `/home/seluser/videos`.

## Starting with OpenShift
```sh
oc run zalenium --image=dosel/zalenium \
    --env="ZALENIUM_KUBERNETES_CPU_REQUEST=250m" \
    --env="ZALENIUM_KUBERNETES_CPU_LIMIT=500m" \
    --env="ZALENIUM_KUBERNETES_MEMORY_REQUEST=1Gi" \
    --overrides='{"spec": {"template": {"spec": {"serviceAccount": "zalenium"}}}}' \
    -l app=zalenium,role=hub --port=4444 -- \
    start --desiredContainers 2 --seleniumImageName [registry ip address]:5000/[kubernetes namespace]/selenium:latest
```

Create the service
```sh
oc create -f ./zalenium-service.yaml
```

In the Openshift console you should then probably create a route. Make sure you have a proper timeout set on the route. Default in Openshift is 30s and most probably this value is to low (pod creation of new selenium nodes might take longer time).

```sh
oc create -f ./zalenium-route.yaml
```

## Example Zalenium Pod Yaml
This is an example of a working zalenium pod with all the relevant mounts attached.

```yaml
​
apiVersion: v1
kind: Pod
metadata:
  name: zalenium-test-15-lsg0v
  generateName: zalenium-test-15-
  labels:
    app: zalenium-test
spec:
  volumes:
    - name: zalenium-videos
      persistentVolumeClaim:
        claimName: zalenium-test-videos
    - name: zalenium-shared
      persistentVolumeClaim:
        claimName: zalenium-shared
  containers:
    - name: zalenium
      image: >-
        172.23.192.79:5000/delivery/zalenium@sha256:f9ac5f4d1dc78811b7b589f0cb16fd198c9c7e562eb149b8c6e60b0686bf150f
      args:
        - start
        - '--desiredContainers'
        - '2'
        - '--screenWidth'
        - '1440'
        - '--screenHeight'
        - '810'
        - '--timeZone'
        - Australia/Canberra
        - '--seleniumImageName'
        - '172.23.192.79:5000/delivery/selenium:latest'
      ports:
        - containerPort: 4444
          protocol: TCP
      env:
        - name: ZALENIUM_KUBERNETES_CPU_REQUEST
          value: 250m
        - name: ZALENIUM_KUBERNETES_CPU_LIMIT
          value: 500m
        - name: ZALENIUM_KUBERNETES_MEMORY_REQUEST
          value: 1Gi
      resources: {}
      volumeMounts:
        - name: zalenium-videos
          mountPath: /home/seluser/videos
        - name: zalenium-shared
          mountPath: /tmp/mounted
      terminationMessagePath: /dev/termination-log
      imagePullPolicy: Always
  restartPolicy: Always
  terminationGracePeriodSeconds: 120
  dnsPolicy: ClusterFirst
  nodeSelector:
    purpose: work
  serviceAccountName: zalenium
  serviceAccount: zalenium
```

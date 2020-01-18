---
title: "Kubernetes" 
bg: tangaroa
color: seashell
icon: img/kubernetes.png
---

Zalenium has support for [Kubernetes](https://kubernetes.io/){:target="_blank"}, these instructions will 
give you an overview of how to get it running. If you find something that needs to be improved, please give us a hand by 
creating a pull request or an issue.

> Kudos to [@pearj](https://github.com/pearj){:target="_blank"} for helping Zalenium work in Kubernetes.

***

### Quick start with Minikube or Minishift (for Openshift)

You can use [Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/){:target="_blank"} 
to deploy locally and get a first impression of Zalenium in Kubernetes. Before starting, you could follow the
[Hello-Minikube](https://kubernetes.io/docs/tutorials/stateless-application/hello-minikube/){:target="_blank"}
tutorial to get familiar with Minikube and make sure it is properly installed.

You will also need [Helm](https://helm.sh/), the standard package manager for Kubernetes.

> Thanks to [@gswallow](https://github.com/gswallow){:target="_blank"} and [@arnaud-deprez](https://github.com/arnaud-deprez) for contributing to Zalenium with the Helm chart.

After starting Minikube locally, follow these steps:
* (Optional) To save time, switch to the Minikube docker daemon and pull the images.
{% highlight shell %}
    eval $(minikube docker-env)
    docker pull elgalu/selenium
    docker pull dosel/zalenium
{% endhighlight %}

* Pull the Zalenium repo to use the files from the Kubernetes folder
{% highlight shell %}
    git clone git@github.com:zalando/zalenium.git
{% endhighlight %}

* Create the deployment in Minikube
{% highlight shell %}
    cd zalenium
    kubectl create namespace zalenium
    helm install zalenium --namespace zalenium charts/zalenium --set hub.serviceType=NodePort
{% endhighlight %}

* Go to the Minikube dashboard and check the deployment, also open the Grid Console
{% highlight shell %}
    # Dashboard
    minikube dashboard
    # Grid Console
    minikube service --namespace zalenium zalenium
{% endhighlight %}

That's it, you can point your tests to the url obtained in the last step.

***

### More implementation details and deployment How Tos

Zalenium integrates with Kubernetes using the
[fabric8 kubernetes-client and openshift-client](https://github.com/fabric8io/kubernetes-client/)
and the initial support was developed on OpenShift, but should be backwards compatible with vanilla Kubernetes and
has been tested on [Minikube](https://github.com/kubernetes/minikube).

#### Service Account

Zalenium uses a service account that is automatically mounted by Kubernetes, it is used to create Selenium pods and 
their related services.

It is a good idea to create a separate service account for specific use by Zalenium, since now most of Kubernetes setup 
uses role based authentication by default, meaning that the service account will need a 
`Role` or `ClusterRole` created that has the necessary privileges to access the parts of 
the Kubernetes API that it needs to.

By default, the helm chart will create a `ServiceAccount` with an appropriate `Role` and `RoleBinding` at the namespace level.
You can see the `Role` that will be created [here](https://github.com/zalando/zalenium/tree/master/charts/zalenium/templates/role.yaml){:target="_blank"}.

If your cluster does not have RBAC enabled, you can disable it with `--set rbac.create=false` and `--set serviceAccount.create=false`.
You can also use a predefined `ServiceAccount` with `--set rbac.create=false` and `--set serviceAccount.create=false` and `--set serviceAccount.name=foo`.
More options are available and explained in the [chart README](https://github.com/zalando/zalenium/tree/master/charts/zalenium/README.md){:target="_blank"}.

#### App label

Zalenium relies on there being an `app="something"` label that it will use to locate `Services` and during `Pod` creation.
This means that you can have multiple zalenium deployments in the same kubernetes namespace that can operate independently
if they have different app labels.

A good default to use would be: `app=zalenium`.

#### Overriding the Selenium Image

For performance reasons it could be a good idea to pull the selenium image, `elgalu/selenium`, into a local registry,
especially since the image will need to be available on potentially any kubernetes node.

<details>
    <summary>For more details about overriding the Selenium image, click here</summary>

    <div class="container m-2 p-2">
        For example, in OpenShift there is a built in registry that can automatically pull the an image from an external registry
        (such as docker hub) 
        <a target="_blank" href="https://docs.openshift.com/container-platform/3.5/dev_guide/managing_images.html#importing-tag-and-image-metadata">on a schedule</a>.
        <br>
        <br>    
        This command will automatically import <code>elgalu/selenium</code> into the OpenShift 
        registry at <code>delivery/selenium:latest</code> updating it on a schedule.
    
{% highlight shell %}
    oc tag docker.io/elgalu/selenium:latest delivery/selenium:latest --scheduled=true
{% endhighlight %}
    
        This would then be available at <code>172.23.192.79:5000/delivery/selenium:latest</code> 
        in the OpenShift registry for example.
        <br>
        <br>
        To use that image, specify 
        <code>--set hub.seleniumImageName="172.23.192.79:5000/delivery/selenium:latest"</code> when 
        processing your <a target="_blank" href="https://helm.sh/">Helm</a> templates.
    </div>        
</details>

#### Auto-mounting the shared folder

Like the Docker version of Zalenium, the Kubernetes version can automatically mount shared folders, the only catch is 
that when you are using persistent volumes you need to make sure that the `Access Mode` is set to `ReadWriteMany`, 
otherwise the selenium nodes will not be able to mount it.

<details>
    <summary>Click here for more details</summary>

    <div class="container m-2 p-2">
        So for example you could create a persistent volume with these contents:
    
{% highlight yaml %}
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
{% endhighlight %}
    
        And a claim like this:
        
{% highlight yaml %}
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
{% endhighlight %}

        Zalenium will scan the <code>volumeMounts</code> for the Zalenium container when 
        it starts up, if it finds mounted volumes it will copy the <code>volume mount</code> 
        information and the linked <code>volume</code> information when it creates a
        Selenium pod.

    </div>        
</details>

#### Managing Resources

Kubernetes has [support](https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/){:target="_blank"} 
for managing how much resources a Pod is allowed to use. Especially when using video recording it is highly recommended 
to specify some resource requests and/or limits otherwise users of your Kubernetes cluster may be negatively affected by 
the Selenium pods.

<details>
    <summary>Click here for more details</summary>

    <div class="container m-2 p-2">

        There are 2 resource requests and 2 resource limits that you can set.  The following table lists the possible values that you can use,
        however, there are no defaults, so if you don't specify anything, no resource limits or requests will be set.
        <br>
        <br>
        <table class="table table-bordered table-striped table-responsive">
          <thead>
            <tr>
              <th style="width: 150px;">Name</th>
              <th style="width: 200px;">Environment Variable</th>
              <th>Example</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>CPU Request</td>
              <td><code>ZALENIUM_KUBERNETES_CPU_REQUEST</code></td>
              <td><code>250m</code> (25% of a CPU core)</td>
            </tr>
            <tr>
              <td>CPU Limit</td>
              <td><code>ZALENIUM_KUBERNETES_CPU_LIMIT</code></td>
              <td><code>500m</code> (50% of a CPU core)</td>
            </tr>
            <tr>
              <td>Memory Request</td>
              <td><code>ZALENIUM_KUBERNETES_MEMORY_REQUEST</code></td>
              <td><code>1Gi</code> (1 Gibibyte)</td>
            </tr>
            <tr>
              <td>Memory Limit</td>
              <td><code>ZALENIUM_KUBERNETES_MEMORY_LIMIT</code></td>
              <td>Probably best to leave empty, because Kubernetes will kill the container if it exceeds the value.</td>
            </tr>
          </tbody>
        </table>

    </div>
</details>    

#### Openshift DeploymentConfig

If you are using Openshift, you might would like to use [Openshift DeploymentConfig](https://docs.okd.io/latest/dev_guide/deployments/how_deployments_work.html){:target="_blank"} instead of [Kubernetes Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/){:target="_blank"}.
Check [here](https://docs.okd.io/latest/dev_guide/deployments/kubernetes_deployments.html#kubernetes-deployments-vs-deployment-configurations){:target="_blank"} for more information on their difference

<details>
    <summary>Click here for more details</summary>

    <div class="container m-2 p-2">
    To do so, you can apply the Helm template as such: 

{% highlight shell %}
    helm template --name zalenium \
        --set hub.serviceType=NodePort \
        --set hub.openshift.deploymentConfig.enabled=true \
        charts/zalenium | oc apply -n zalenium -f -
{% endhighlight %}

    Be careful to delete the Kubernetes Deployment before, otherwise the 2 zalenium instannce will get in concurrence when create selenium pods.

    This will create a DeploymentConfig with a deployment trigger `ConfigChange`, which is more or less equivalent to what Kubernetes Deployment is doing, which means redeploying 
    your application if the config has changed.

    If you want to add another trigger like an Image, you can create a yaml file `openshift-values.yaml` with all your parameters such as: 

{% highlight yaml %}
    hub:
      serviceType: NodePort
      openshift:
        deploymentConfig:
          enabled: true
          triggers:
            - type: "ConfigChange"
            - type: "ImageChange"
              imageChangeParams:
                automatic: true
                from:
                  kind: "ImageStreamTag"
                  name: "zalenium:latest"
                containerNames:
                  - "zalenium"
{% endhighlight %}

    And then apply the helm template with this file: 

{% highlight shell %}
    helm template --name zalenium -f openshift-values.yaml charts/zalenium | oc apply -n zalenium -f -
{% endhighlight %}

    This will create a DeploymentConfig that will rollout a new Pod whenever the configuration has changed or a new image zalenium:latest has been pushed in the internal docker registry of Openshift.
    </div>
</details>

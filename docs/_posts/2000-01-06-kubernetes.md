---
title: "Kubernetes" 
bg: tangaroa
color: seashell
icon: img/kubernetes.png
---

Zalenium has support for [Kubernetes](https://kubernetes.io/), these instructions will give you an
overview of how to get it running. If you find something that needs to be improved, please give us a hand by creating
a pull request or an issue.

***

### Quick start with Minikube

You can use [Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/) to deploy locally and get a first 
impression of Zalenium in Kubernetes. Before starting, you could follow the
[Hello-Minikube](https://kubernetes.io/docs/tutorials/stateless-application/hello-minikube/) tutorial to get familiar
with Minikube and make sure it is properly installed.

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
    kubectl create -f kubernetes/
{% endhighlight %}

* Go to the Minikube dashboard and check the deployment, also open the Grid Console
{% highlight shell %}
    # Dashboard
    minikube dashboard
    # Grid Console
    minikube service zalenium
{% endhighlight %}

That's it, you can point your tests to the url obtained in the last step.

***

### Deploying with Helm
[Helm](https://helm.sh) is a tool that greatly simplifies installing apps on a Kubernetes cluster.  Helm users can 
see an example of a Helm chart that installs Zalenium grid in the [k8s/helm](k8s/helm) 
directory.  Support can be added for different storage classes, RBAC support (and/or OpenShift).

***

### More implementation details and deployment How Tos

Zalenium integrates with Kubernetes using the
[fabric8 kubernetes-client and openshift-client](https://github.com/fabric8io/kubernetes-client/)
and the initial support was developed on OpenShift, but should be backwards compatible with vanilla Kubernetes and
has been tested on [Minikube](https://github.com/kubernetes/minikube). 

#### Service Account

Zalenium uses a service account that is automatically mounted by Kubernetes, it is used to create Selenium pods and 
their related services.

It is a good idea to create a separate service account for specific use by Zalenium, especially when running inside
OpenShift because it uses role based authentication by default, meaning that the service account will need a 
<code class="bg-light text-dark">ClusterRole</code> created that has the necessary privileges to access the parts of 
the Kubernetes API that it needs to.

<details>
    <summary>Click to see service account creation in Kubernetes</summary>

    <div class="container m-2 p-2">
        Create the Zalenium service account, this should be enough for Minikube.
{% highlight shell %}
    kubectl create sa zalenium
{% endhighlight %}
        
        Starting from Kubernetes 1.6, there is beta [RBAC support](http://blog.kubernetes.io/2017/04/rbac-support-in-kubernetes.html)
        (Role Based Access Control), it is possible that this may be similar to the built-in RBAC support in OpenShift.
        If you want to use RBAC support in Kubernetes, you could try adapting the OpenShift instructions below.
    </div>     
    
</details>

<details>
    <summary>Click to see service account creation in OpenShift</summary>

    <div class="container m-2 p-2">
        First up, create the [cluster role](https://github.com/zalando/zalenium/blob/master/docs/k8s/zalenium-role.json):
        
{% highlight shell %}
    oc create -f zalenium-role.json
{% endhighlight %}

        Then create the Zalenium service account:

{% highlight shell %}
    oc create sa zalenium
{% endhighlight %}

        Then allow the Zalenium service account to run as any user, as Zalenium presently needs to run as root, 
        which OpenShift doesn't allow by default.

{% highlight shell %}
    oc adm policy add-scc-to-user anyuid -z zalenium
{% endhighlight %}

        Next add the <code class="bg-light text-dark">zalenium-role</code> you just created to the Zalenium service 
        account.
{% highlight shell %}
    oc adm policy add-role-to-user zalenium-role -z zalenium
{% endhighlight %}
        
        In case you get a message similar to this one:
{% highlight shell %}
    Error from server (NotFound): role.authorization.openshift.io "exampleview" not found
{% endhighlight %}
        
        Check the namespace where you deployment is and add it to the previous command, e.g.:
    {% highlight shell %}
    # Check namespaces
    oc get namespace
    # Execute command
    Error from server (NotFound): role.authorization.openshift.io "exampleview" not found
    oc adm policy add-role-to-user zalenium-role -z zalenium --role-namespace='your_deployment_namespace'
    {% endhighlight %}
    </div>     
    
</details>


#### App label
Zalenium relies on there being an <code class="bg-light text-dark">app="something"</code> label that it will use to 
locate <code class="bg-light text-dark">Services</code> and during <code class="bg-light text-dark">Pod</code> creation.
This means that you can have multiple zalenium deployments in the same kubernetes namespace that can operate independently
if they have different app labels.

A good default to use would be: <code class="bg-light text-dark">app=zalenium</code>.

#### Overriding the Selenium Image
For performance reasons it could be a good idea to pull the selenium image, 
<code class="bg-light text-dark">elgalu/selenium</code>, into a local registry,
especially since the image will need to be available on potentially any kubernetes node.


<details>
    <summary>For more deails about overriding the Selenium image, click here</summary>

    <div class="container m-2 p-2">
        In Openshift there is a built in registry that can automatically pull the an image from an external registry
        (such as docker hub) 
        <a target="_blank" href="https://docs.openshift.com/container-platform/3.5/dev_guide/managing_images.html#importing-tag-and-image-metadata"><u>on a schedule</u></a>.
        <br>
        <br>    
        This command will automatically import <code class="bg-light text-dark">elgalu/selenium</code> into the OpenShift 
        registry at <code class="bg-light text-dark">delivery/selenium:latest</code> updating it on a schedule.
    
{% highlight shell %}
    oc tag docker.io/elgalu/selenium:latest delivery/selenium:latest --scheduled=true
{% endhighlight %}
    
        This would then be available at <code class="bg-light text-dark">172.23.192.79:5000/delivery/selenium:latest</code> 
        in the OpenShift registry for example.
        <br>
        <br>
        To use that image, specify 
        <code class="bg-light text-dark">--seleniumImageName 172.23.192.79:5000/delivery/selenium:latest</code> when 
        starting Zalenium.
    </div>        
</details>

#### Auto-mounting the shared folder
Like the Docker version of Zalenium, the Kubernetes version can automatically mount shared folders, the only catch is 
that when you are using persistent volumes you need to make sure that the <code class="bg-light text-dark">Access Mode</code> 
is set to <code class="bg-light text-dark">ReadWriteMany</code>, otherwise the selenium nodes will not be able to mount it.

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

        Zalenium will scan the <code class="bg-light text-dark">volumeMounts</code> for the Zalenium container when 
        it starts up, if it finds mounted volumes it will copy the <code class="bg-light text-dark">volume mount</code> 
        information and the linked <code class="bg-light text-dark">volume</code> information when it creates a
        Selenium pod.

    </div>        
</details>

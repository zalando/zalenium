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

### Quick start with Minikube

You can use [Minikube](https://kubernetes.io/docs/getting-started-guides/minikube/){:target="_blank"} 
to deploy locally and get a first impression of Zalenium in Kubernetes. Before starting, you could follow the
[Hello-Minikube](https://kubernetes.io/docs/tutorials/stateless-application/hello-minikube/){:target="_blank"}
tutorial to get familiar with Minikube and make sure it is properly installed.

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
    minikube service --namespace zalenium zalenium
{% endhighlight %}

That's it, you can point your tests to the url obtained in the last step.

***

### Deploying with Helm

> Thanks to [@gswallow](https://github.com/gswallow){:target="_blank"} for contributing to Zalenium with the Helm chart.

[Helm](https://helm.sh){:target="_blank"} is a tool that greatly simplifies installing apps on a Kubernetes cluster. 
Helm users can see an example of a Helm chart that installs Zalenium grid in the 
[k8s/helm](https://github.com/zalando/zalenium/tree/master/docs/k8s/helm){:target="_blank"} directory. Support can be 
added for different storage classes, RBAC support (and/or OpenShift).

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
`ClusterRole created that has the necessary privileges to access the parts of 
the Kubernetes API that it needs to.

<details>
    <summary>Click to see service account creation in Kubernetes</summary>

    <div class="container m-2 p-2">
        Create the Zalenium service account, this should be enough for Minikube.
{% highlight shell %}
    kubectl create sa zalenium
{% endhighlight %}
        
        Starting from Kubernetes 1.6, there is beta <a target="_blank" href="http://blog.kubernetes.io/2017/04/rbac-support-in-kubernetes.html">RBAC support</a>
        (Role Based Access Control), it is possible that this may be similar to the built-in RBAC support in OpenShift.
        If you want to use RBAC support in Kubernetes, you could try adapting the OpenShift instructions below.
    </div>     
    
</details>

<details>
    <summary>Click to see service account creation in OpenShift</summary>

    <div class="container m-2 p-2">
        First up, create the <a target="_blank" href="https://github.com/zalando/zalenium/blob/master/docs/k8s/zalenium-role.json">cluster role</a>:
        
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

        Next add the <code>zalenium-role</code> you just created to the Zalenium service 
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
Zalenium relies on there being an `app="something"` label that it will use to locate `Services` and during `Pod` creation.
This means that you can have multiple zalenium deployments in the same kubernetes namespace that can operate independently
if they have different app labels.

A good default to use would be: `app=zalenium`.

#### Overriding the Selenium Image
For performance reasons it could be a good idea to pull the selenium image, `elgalu/selenium`, into a local registry,
especially since the image will need to be available on potentially any kubernetes node.


<details>
    <summary>For more deails about overriding the Selenium image, click here</summary>

    <div class="container m-2 p-2">
        In OpenShift there is a built in registry that can automatically pull the an image from an external registry
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
        <code>--seleniumImageName 172.23.192.79:5000/delivery/selenium:latest</code> when 
        starting Zalenium.
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

*** 

## Getting Started Guidelines

#### Vanilla Kubernetes

<details>
    <summary>Click here for more details</summary>

    <div class="container m-2 p-2">

        Create the deployment:

{% highlight bash %}
    kubectl run zalenium \
        --image=dosel/zalenium \
        --overrides='{"spec": {"template": {"spec": {"serviceAccount": "zalenium"}}}}' \
        -l app=zalenium,role=grid \
        -- start --desiredContainers 2
{% endhighlight %}
        
        Create the services

{% highlight bash %}
    kubectl create service nodeport zalenium-grid --tcp=4444:4444 --dry-run -o yaml \
        | kubectl label --local -f - app=zalenium --overwrite -o yaml \
        | kubectl set selector --local -f - app=zalenium,role=grid -o yaml \
        | grep -v "running in local/dry-run mode" \
        | kubectl create -f -
{% endhighlight %}

    Then you can open the grid in minikube by running
    
{% highlight bash %}
    minikube service zalenium-grid
{% endhighlight %}
    
    For videos to work you need to mount in <code>/home/seluser/videos</code>.
    </div>
</details>    

#### OpenShift

<details>
    <summary>Click here for more details</summary>

    <div class="container m-2 p-2">

        Create the deployment:

{% highlight bash %}
    oc run zalenium --image=dosel/zalenium \
        --env="ZALENIUM_KUBERNETES_CPU_REQUEST=250m" \
        --env="ZALENIUM_KUBERNETES_CPU_LIMIT=500m" \
        --env="ZALENIUM_KUBERNETES_MEMORY_REQUEST=1Gi" \
        --overrides='{"spec": {"template": {"spec": {"serviceAccount": "zalenium"}}}}' \
        -l app=zalenium,role=hub --port=4444 -- \
        start --desiredContainers 2 --seleniumImageName [registry ip address]:5000/[kubernetes namespace]/selenium:latest
{% endhighlight %}
        
        Create the service
{% highlight bash %}
    oc create -f ./zalenium-service.yaml
{% endhighlight %}

In the OpenShift console you should then probably create a route. Make sure you have a proper timeout set on the route. Default in OpenShift is 30s and most probably this value is to low (pod creation of new selenium nodes might take longer time).

{% highlight bash %}
    oc create -f ./zalenium-route.yaml
{% endhighlight %}

    </div>    
</details>    

#### Google Container Engine (GKE)

<details>
    <summary>Click here for more details</summary>

    <div class="container m-2 p-2">
    
        <blockquote class="blockquote">
            <p class="mb-0">
                Thanks to <a target="_blank" href="https://github.com/laszlocph">@laszlocph</a> for contributing this section.
            </p>
        </blockquote>

        This guide can be used in addition to the information provided in the sections above.
        <br>
        <br>
        <h5>Prerequisites</h5>

        <ul>
            <li>You have to have a Google Container Engine account with billing enabled</li>
            <li>And a project created on the <a target="_blank" href="https://console.cloud.google.com/kubernetes">GKE dashboard</a></li>
            <li>The Google Cloud SDK with the <code>gcloud</code> tool must be present on 
            your machine and configured to the previously created project</li>
            <li><code>kubectl</code> has to be installed on your machine</li>
        </ul>            

        Follow the <a target="_blank" href="https://cloud.google.com/container-engine/docs/quickstart">Quickstart for Google Container Engine</a> to set these up.
        <br>
        <br>
        <h5>Creating a Kubernetes cluster</h5>

{% highlight bash %}
    
    gcloud container clusters create zalenium
    
    ...

    Creating cluster zalenium...done.
    Created [https://container.googleapis.com/v1/projects/xxx/zones/europe-west3-c/clusters/zalenium].
    kubeconfig entry generated for zalenium.
    NAME      ZONE            MASTER_VERSION  MASTER_IP      MACHINE_TYPE   NODE_VERSION  NUM_NODES  STATUS
    zalenium  europe-west3-c  1.6.9           aaa.bb.xxx.yy  n1-standard-1  1.6.9         3          RUNNING
    
    
{% endhighlight %}



    Then activate the kubeconfig profile with

{% highlight bash %}
    
    gcloud container clusters get-credentials zalenium
    
    ...
    
    Fetching cluster endpoint and auth data.
    kubeconfig entry generated for zalenium.
    
{% endhighlight %}


        Verify the kubectl config with <code>kubectl get pods --all-namespaces</code> command.
        
        <br>
        <br>
        <h5 class="font-weight-bold">Zalenium Plumbing</h5>
        <br>
    
        Zalenium uses a Kubernetes ServiceAccount to create pods on-demand. As explained in the section above, we have to 
        create the ServiceAccount, and we have to grant the required permissions to that account. To be able to create the 
        roles and the necessary bindings the GKE setup has a 
        <a target="_blank" href="https://github.com/coreos/prometheus-operator/issues/357">special step</a>,
        we have to make our users a cluster-admin.

{% highlight bash %}   
    kubectl create clusterrolebinding <Arbitrary name for the binding, use your nickname> \
        --clusterrole=cluster-admin --user=<your google cloud login email>
{% endhighlight %}

        Then create the necessary constructs. it also creates a Namespaces, called <code>zalenium</code>.
        You can find the <code>plumbing.yaml</code> file 
        <a target="_blank" href="https://github.com/zalando/zalenium/blob/master/docs/k8s/gke/plumbing.yaml">here</a>.    

{% highlight bash %}    
    kubectl apply -f plumbing.yaml
{% endhighlight %}

        For the video files, a PersistentVolume has to be created also. The <code>pv.yaml</code> 
        file can be found <a target="_blank" href="https://github.com/zalando/zalenium/blob/master/docs/k8s/gke/pv.yaml">here</a>.

{% highlight bash %}
    kubectl apply -f pv.yaml
{% endhighlight %}

        Change the kubectl context to "zalenium".

{% highlight bash %}
    kubectl config set-context $(kubectl config current-context) --namespace=zalenium
{% endhighlight %}

        <h5 class="font-weight-bold">Launch Zalenium</h5>
        <br>

        Find the <code>zalenium.yaml</code> file 
        <a target="_blank" href="https://github.com/zalando/zalenium/blob/master/docs/k8s/gke/zalenium.yaml">here</a>.

{% highlight bash %}
    kubectl apply -f zalenium.yaml
{% endhighlight %}


        Then watch as the pods are created with <code>kubectl get pods</code>.

{% highlight bash %}
    ➜  yaml git:(kubernetes) ✗ kubectl get pods
    NAME                        READY     STATUS    RESTARTS   AGE
    zalenium-2238551656-c0w17   1/1       Running   0          4m
    zalenium-40000-17d5v        1/1       Running   0          3m
    zalenium-40001-xnqdr        1/1       Running   0          3m
{% endhighlight %}

        You can also follow the logs with <code>kubectl logs -f zalenium-2238551656-c0w17</code>.

        <br>
        <br>       
        <h5 class="font-weight-bold">Accessing Zalenium</h5>
        <br>
        
        <h5>NodePort</h5>
        <br>

        Kubernetes provides <a target="_blank" href="https://kubernetes.io/docs/concepts/services-networking/service/#publishing-services---service-types">multiple ways</a> 
        to route external traffic to the deployed services. NodePort being the most simple one and by default that is 
        enabled in the <a target="_blank" href="https://github.com/zalando/zalenium/blob/master/docs/k8s/gke/zalenium.yaml">zalenium.yaml</a> file.
        <br>
        <br>
        NodePort is picking a random port in the default port range (30000-32767) and makes sure that if a request is 
        hitting that port on <strong>any</strong> of the cluster nodes, it gets routed to the deployed pod.

{% highlight bash %}
    $ kubectl get svc
    NAME                   CLUSTER-IP      EXTERNAL-IP   PORT(S)           AGE
    zalenium               10.43.251.95    <nodes>       4444:30714/TCP    4m
    zalenium-40000-058z8   10.43.247.200   <nodes>       50000:30862/TCP   50s
    zalenium-40001-853mq   10.43.244.122   <nodes>       50001:30152/TCP   46s
{% endhighlight %}

        The above console output lists all services in the zalenium namespace and you can see that the hub is exposed on 
        port 30714, and the two browser nodes on 30862 and 30152.
        <br>
        <br>
        To access the service first you have to locate the IP address of one of the cluster nodes. The GKE cluster is 
        built on standard Google Cloud VMs, so to find a node you have to go to the 
        <a target="_blank" href="https://console.cloud.google.com/compute/instances">GCloud dashboard</a> and 
        copy an IP a node address.

        <br>
        <br>
        <img alt="Google Cloud VMs" src="./k8s/gke/vm.png">
        <br>
        <br>

        In addition to that, you have to open the GCloud firewall too. To keep the rules flexible, but somewhat tight, 
        the example bellow opens the firewall from a source range of IPs to all of the NodePort variations. Adjust it 
        to your needs.

{% highlight bash %}
    gcloud compute firewall-rules create zalenium \
        --allow tcp:30000-32767 --source-ranges=83.94.yyy.xx/32
{% endhighlight %}

        Zalenium is accessible on the <code>http://35.198.142.117:30714/grid/console</code> address 
        in the example.
        <br>
        <br>
        The dashboard on <code>http://35.198.142.117:30714/dashboard/</code> and the "live" page 
        on <code>http://35.198.142.117:30714/grid/admin/live</code>

        <h5>Troubleshooting</h5>

        In any case you would like to recreate the service the following one liners can assist you:
        <br>
        <br>

        To delete the PersistentVolume and all Zalenium deployments.
{% highlight bash %}
    kubectl delete -f pv.yaml && kubectl delete -f zalenium.yaml
{% endhighlight %}

        Then to recreate them
{% highlight bash %}
    kubectl apply -f pv.yaml && kubectl apply -f zalenium.yaml
{% endhighlight %}
    
    </div>    
</details>    

#### Example Zalenium Pod Example

<details>
    <summary>Click here for more details</summary>

    <div class="container m-2 p-2">
        This is an example of a working zalenium pod with all the relevant mounts attached.

{% highlight yaml %}
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

{% endhighlight %}

    </div>
</details>   

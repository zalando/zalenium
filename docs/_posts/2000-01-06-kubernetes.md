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

---
title: "Contributing" 
bg: tangaroa
color: seashell
fa-icon: handshake-o
---


### Contributions

**Thank you for your interest in making this project even better and more awesome. Your contributions are highly welcomed.**

If you need help, please open a GitHub Issue in this project. If you work at Zalando reach out to us at team-tip.

#### Report a bug
Reporting bugs is one of the best ways to contribute. Before creating a bug report, please check that an 
[issue](https://github.com/zalando/zalenium/issues/new){:target="_blank"} reporting the same problem does 
not already exist. If there is an such an issue, you may add your information as a comment.

To report a new bug, open an issue that summarizes the bug and set the label to "bug".

If you want to provide a fix along with your bug report: That is great! In this case please send us a pull request as 
described in section **Contribute Code**.

#### Suggest a Feature
To request a new feature, open an [issue](https://github.com/zalando/zalenium/issues/new){:target="_blank"} 
and summarize the desired functionality and its use case. Set the issue label to "feature".

#### Contribute Code
This is a rough outline of what the workflow for code contributions looks like:
- Check the list of open [issues](https://github.com/zalando/zalenium/issues/new){:target="_blank"}. Either 
assign an existing issue to yourself, or create a new one that you would like work on and discuss your ideas and use cases.
- Fork the repository
- Create a topic branch from where you want to base your work. This is usually master.
- Make commits of logical units.
- Write good commit messages (see below).
- Push your changes to a topic branch in your fork of the repository.
- Submit a pull request
- Your pull request must receive a <i class="fa fa-thumbs-o-up" aria-hidden="true"></i> from two
[maintainers](https://github.com/zalando/zalenium/blob/master/MAINTAINERS){:target="_blank"} 

Thanks for your contributions!

#### Commit messages
Your commit messages ideally can answer two questions: what changed and why. The subject line should feature the 
“what” and the body of the commit should describe the “why”.

When creating a pull request, its comment should reference the corresponding issue id.

**Have fun and enjoy hacking!**

*** 

### Code of Conduct

We have adopted the Contributor Covenant as the code of conduct for this project:

[http://contributor-covenant.org/version/1/4/](http://contributor-covenant.org/version/1/4/){:target="_blank"}

***

### Building and Testing

If you want to verify your changes locally with the existing tests (please double check that the Docker daemon is
running):

* Unit tests

{% highlight shell %}
    mvn clean test
{% endhighlight %}

* Building the image

{% highlight shell %}
    mvn clean package
    cd target
    docker build -t zalenium:YOUR_TAG .
{% endhighlight %}

* Running the image you just built

{% highlight shell %}
    docker run --rm -ti --name zalenium -p 4444:4444 \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v /tmp/videos:/home/seluser/videos \
        --privileged zalenium:YOUR_TAG start
{% endhighlight %}


* Running the integration tests with Sauce Labs or BrowserStack or TestingBot. You will need an account on any of those providers 
to run them (they have free plans). 
{% highlight shell %}
    ./run_integration_tests.sh sauceLabs|browserStack|testingBot
{% endhighlight %}

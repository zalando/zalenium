[![Build Status](https://travis-ci.org/zalando/zalenium.svg?branch=master)](https://travis-ci.org/zalando/zalenium)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/c719a14f5537488b8fb95d70e27acd5f)](https://www.codacy.com/app/diemol_zalenium/zalenium?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=zalando/zalenium&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/zalando/zalenium/branch/master/graph/badge.svg)](https://codecov.io/gh/zalando/zalenium)
[![](https://images.microbadger.com/badges/version/dosel/zalenium.svg)](https://microbadger.com/images/dosel/zalenium)
[![](https://images.microbadger.com/badges/version/dosel/zalenium:2.53.1u.svg)](https://microbadger.com/images/dosel/zalenium:2.53.1u)
[![Gitter](https://badges.gitter.im/zalando/zalenium.svg)](https://gitter.im/zalando/zalenium?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)


# What is Zalenium?
A Selenium Grid extension to scale up and down your local grid dynamically with docker containers. It uses 
[docker-selenium](https://github.com/elgalu/docker-selenium) to run your tests in Firefox and Chrome locally, and when 
you need a different browser, your tests get redirected to [Sauce Labs](https://saucelabs.com/) and/or 
[BrowserStack](https://www.browserstack.com/) and/or [TestingBot](https://testingbot.com/).

### Why Zalenium?
We know how complicated is to have a stable grid to run UI tests with Selenium, and moreover how hard is to maintain 
it over time. It is also very difficult to have a local grid with enough capabilities to cover all browsers and platforms.

Therefore we are trying this approach where [docker-selenium](https://github.com/elgalu/docker-selenium) nodes are 
created, used and disposed on demand when possible. With this, you can run faster your UI tests with Firefox and Chrome 
since they are running on a local grid, on a node created from scratch and disposed after the test finishes.

And whenever you need a capability that cannot be fulfilled by [docker-selenium](https://github.com/elgalu/docker-selenium), 
the test gets redirected to [Sauce Labs](https://saucelabs.com/) and/or [BrowserStack](https://www.browserstack.com/) 
and/or [TestingBot](https://testingbot.com/).

This creates Zalenium's main goal: to allow anyone to have a disposable and flexible Selenium Grid infrastructure.

The original idea comes from this [Sauce Labs post](https://saucelabs.com/blog/introducing-the-sauce-plugin-for-selenium-grid).

You can already use Zalenium, nevertheless, more features are added often, therefore we invite 
you to test it, to contribute, to report bugs, and suggest any ideas you may have, see [contributing](CONTRIBUTING.md) 
for more details.

## Getting Started

#### Prerequisites
* Docker engine running, version >= 1.11.1 (probably works with earlier versions, not tested yet).
* Pulling the [docker-selenium](https://github.com/elgalu/docker-selenium) image. `docker pull elgalu/selenium`
* *nix platform (tested only in OSX and Ubuntu, not tested on Windows yet).
* If you want to use the [Sauce Labs](https://saucelabs.com/) and/or the [BrowserStack](https://www.browserstack.com/) 
and/or the [TestingBot](https://testingbot.com/) feature, you need an account with them.

#### Setting it up
* Make sure your docker daemon is running
* `docker pull dosel/zalenium`

#### Running it
Zalenium uses docker to scale on-demand, therefore we need to give it the `docker.sock` full access, this is known as 
"Docker alongside docker".

* Basic usage, without any of the integrated cloud testing platforms enabled:

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      dosel/zalenium start 
  ```

* You can also try our one line installer and starter (it will check for the latest images and ask for missing 
dependencies.)

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s start
  ```

* More usage examples, more parameters, configurations, video usage and one line starters can be seen [here](./docs/usage_examples.md)

* After the output, you should see the DockerSeleniumStarter node in the [grid](http://localhost:4444/grid/console)

* Stop it: `docker stop zalenium`

#### Using it
* Just point your Selenium tests to [http://localhost:4444/wd/hub](http://localhost:4444/wd/hub) and that's it!
* You can use the [integration tests](./src/test/java/de/zalando/tip/zalenium/it/ParallelIT.java) we have to try Zalenium.
* Check the live preview of your running tests [http://localhost:4444/grid/admin/live](http://localhost:4444/grid/admin/live)
* Check the recorded videos in the `/tmp/videos` folder (or the one you mapped when starting Zalenium). More details about the videos 
feature can be seen [here](./docs/usage_examples.md#video-feature)
* __[BETA]__ Check the [dashboard](http://localhost:5555) to see all the recorded and downloaded videos (available after 
the 1st video is ready). 

### Docker version

#### Linux
For Linux systems you can simply share the docker binary via `-v $(which docker):/usr/bin/docker`

```sh
docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
  -v $(which docker):/usr/bin/docker \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /tmp/videos:/home/seluser/videos \
  dosel/zalenium start 
```

#### OSX
Zalenium for OSX is currently compatible with Docker `1.11`, `1.12` __default__ and `1.13`. In Mac is recommended that 
you explicitly tell Zalenium which major version you are using via `-e DOCKER=1.11` due to API compatibility issues. 
In the future this will be automated on our side as it is with Linux (read above)

```sh
docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
  -e DOCKER=1.11 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /tmp/videos:/home/seluser/videos \
  dosel/zalenium start 
```

## Contributions
Any feedback or contributions are welcome! Please check our [guidelines](CONTRIBUTING.md), they just follow the general 
GitHub issue/PR flow.

#### Building and Testing

If you want to verify your changes locally with the existing tests (please double check that the Docker daemon is 
running and that you can do `docker ps`):
* Unit tests

    ```sh
        mvn clean test
    ```
* Building the image

    ```sh
        mvn clean package
        cd target
        docker build -t zalenium:YOUR_TAG .
    ```
* Running the image you just built
    ```sh
      docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v /tmp/videos:/home/seluser/videos \
        zalenium:YOUR_TAG start 
    ```
* Running the integration tests with Sauce Labs or BrowserStack or TestingBot. You will need an account on any of those providers to run them (they have free plans). Or you can just run some of our [tests](./src/test/java/de/zalando/tip/zalenium/it/ParallelIT.java)  individually from an IDE. 
    ```sh
        ./run_integration_tests.sh sauceLabs|browserStack|testingBot
    ```


## How it works

![How it works](./images/how_it_works.gif)

Zalenium works conceptually in a simple way:

1. A Selenium Hub is started, listening to port 4444.
2. One custom node for [docker-selenium](https://github.com/elgalu/docker-selenium), and when enabled, one for 
[Sauce Labs](https://saucelabs.com/) and/or one for [BrowserStack](https://www.browserstack.com/) and/or one for 
[TestingBot](https://testingbot.com) are started and get registered to the grid.
3. When a test request arrives to the hub, the requested capabilities are verified against each one of the nodes.
4. If the request can be executed on [docker-selenium](https://github.com/elgalu/docker-selenium), a docker container 
is created on the run, and the test request is sent back to the hub while the new node registers.
5. After the hub acknowledges the new node, it processes the test request with it.
6. The test is executed, and then container is disposed.
7. If the test cannot be executed in [docker-selenium](https://github.com/elgalu/docker-selenium), it will processed by 
one of the enabled cloud testing platforms. It takes the HTTP request, adds the user and api key to it, and forwards it 
to the cloud platform.

Basically, the tool makes the grid expand or contract depending on the amount of requests received.

## Selenium 2 and Selenium 3 - About the project versioning
* To make life easy for people who want to use Zalenium, we are now using as a version number the Selenium version 
being supported.
* The major-minor version combined with the patch level will indicate the Selenium version being supported. E.g.
  * When a release is `2.53.1a`, it supports Selenium 2.53.1
  * When a release is `3.2.0a`, it supports Selenium 3.2.0
  * The badges above show the latest image versions for Selenium 2 and 3
  * Alias for the latest Selenium 2 and 3 images, use `dosel/zalenium:2` and `dosel/zalenium:3`.

* From version `3.2.0a`, `latest` is pointing to the most recent docker image supporting Selenium 3 (GA data shows now 
slight more usage of Selenium 3 than Selenium 2.)

* Version `2.53.1u` is the last version/image supporting Selenium 2.53.1.

## Zalenium in the Selenium Conf Austin 2017
Get a better overview of what Zalenium is and how it works by checking the recorded talk [here](https://www.youtube.com/watch?v=W5qMsVrob6I)

## Integrated Cloud Testing solutions
* Thanks to the open source accounts we got, we have integrated so far:

![BrowserStack](./images/browserstack_logo.png)    ![Sauce Labs](./images/saucelabs_logo.png)     ![TestingBot](./images/testingbot_logo.png)

If you want to integrate another cloud testing solution, we are happy to receive PRs or requests via issues, don't 
forget to check the [guidelines](CONTRIBUTING.md) for contributing.

License
===================

See [License](LICENSE.md)

# Usage Examples

* [Initial setup](#initial-setup)
* [Starting Zalenium](#starting-zalenium)
  * [with Sauce Labs enabled](#with-sauce-labs-enabled)
  * [with BrowserStack enabled](#with-browserstack-enabled)
  * [with TestingBot enabled](#with-testingbot-enabled)
  * [with screen width and height, and time zone](#with-screen-width-and-height-and-time-zone)
* [Starting Zalenium with Docker Compose](#starting-zalenium-with-docker-compose)


## Initial setup

* Make sure your docker daemon is running
* Also double check that you have pulled the needed images:
  * `docker pull dosel/zalenium`
  * `docker pull elgalu/selenium`


## Starting Zalenium

Basic usage, without any of the integrated cloud testing platforms.

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      dosel/zalenium start 
  ```

### with Sauce Labs enabled

  ```sh
    export SAUCE_USERNAME=<your Sauce Labs username>
    export SAUCE_ACCESS_KEY=<your Sauce Labs access key>
    docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
      -e SAUCE_USERNAME -e SAUCE_ACCESS_KEY \
      -v /tmp/videos:/home/seluser/videos \
      -v /var/run/docker.sock:/var/run/docker.sock \
      dosel/zalenium start --sauceLabsEnabled true
  ```

### with BrowserStack enabled

  ```sh
    export BROWSER_STACK_USER=<your BrowserStack username>
    export BROWSER_STACK_KEY=<your BrowserStack access key>
    docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
      -e BROWSER_STACK_USER -e BROWSER_STACK_KEY \
      -v /tmp/videos:/home/seluser/videos \
      -v /var/run/docker.sock:/var/run/docker.sock \
      dosel/zalenium start --browserStackEnabled true
  ```

### with TestingBot enabled

  ```sh
    export TESTINGBOT_USER=<your TestingBot username>
    export TESTINGBOT_KEY=<your TestingBot access key>
    docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
      -e TESTINGBOT_KEY -e TESTINGBOT_SECRET \
      -v /tmp/videos:/home/seluser/videos \
      -v /var/run/docker.sock:/var/run/docker.sock \
      dosel/zalenium start --testingBotEnabled true
  ```

### with screen width and height, and time zone

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 -p 5555:5555 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      dosel/zalenium start --screenWidth 1440 --screenHeight 810 --timeZone "America/Montreal"
  ```

## Starting Zalenium with Docker Compose

You can see an example [here](./docs/docker-compose.yaml)
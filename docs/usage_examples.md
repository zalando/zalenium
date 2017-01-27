# Usage Examples

* [Initial setup](#initial-setup)
* [Starting Zalenium](#starting-zalenium)
  * [with Sauce Labs enabled](#with-sauce-labs-enabled)
  * [with BrowserStack enabled](#with-browserstack-enabled)
  * [with TestingBot enabled](#with-testingbot-enabled)
  * [with screen width and height, and time zone](#with-screen-width-and-height-and-time-zone)
* [Starting Zalenium with Docker Compose](#starting-zalenium-with-docker-compose)
* [One line starters](#one-line-starters)
  * [Zalenium one-liner installer](#zalenium-one-liner-installer)
  * [Install and start](#install-and-start)
  * [Install and start with latest Selenium 3](#install-and-start-with-latest-selenium-3)
  * [Install and start with latest Selenium 2](#install-and-start-with-latest-selenium-2)
  * [Install and start a specific version](#install-and-start-a-specific-version)
  * [Cleanup](#cleanup)
* [Live preview](#live-preview)
  * [Displaying the live preview](#displaying-the-live-preview)
  * [Showing the test name on the live preview](#showing-the-test-name-on-the-live-preview)
  * [Filtering tests by group name](#filtering-tests-by-group-name)


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
    export TESTINGBOT_KEY=<your TestingBot access key>
    export TESTINGBOT_SECRET=<your TestingBot secret>
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

You can see an example [here](./docker-compose.yaml)

## One line starters

### Zalenium one-liner installer

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash
  ```
  
### Install and start

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s start
  ```

### Install and start with latest Selenium 3

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s 3 start
  ```

### Install and start with latest Selenium 2

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s 2 start
  ```

### Install and start a specific version

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s 3.0.1a start
  ```

### Cleanup

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s stop
  ```

## Live preview

### Displaying the live preview
* Just go to [http://localhost:4444/grid/admin/live](http://localhost:4444/grid/admin/live)
  * You can also replace `localhost` for the IP/machine name where Zalenium is running.
* Auto-refresh, add `?refresh=numberOfSeconds` to refresh the view automatically. E.g. 
[http://localhost:4444/grid/admin/live?refresh=20](http://localhost:4444/grid/admin/live?refresh=20) will refresh the 
page every 20 seconds.

### Showing the test name on the live preview
Add a `name` capability with the test name to display it in the live preview. This helps to identify where your test 
is running. Example code in Java for the capability:

  ```java
    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
    desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
    desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);
    desiredCapabilities.setCapability("name", "myTestName");
  ```

### Filtering tests by group name
When more than one develper/tester is using the same instance of Zalenium, add a `group` capability to your tests. This 
will let you filter the running tests in the live preview by passing `?group=myTestGroup` at the end of the url. E.g.
 
* Added capability

  ```java
    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
    desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
    desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);
    desiredCapabilities.setCapability("group", "myTestGroup");
  ```

* Filter in live preview: [http://localhost:4444/grid/admin/live?group=myTestGroup](http://localhost:4444/grid/admin/live?group=myTestGroup)



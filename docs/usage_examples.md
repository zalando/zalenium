# Usage Examples

* [Initial setup](#initial-setup)
* [Starting Zalenium](#starting-zalenium)
  * [with Sauce Labs enabled](#with-sauce-labs-enabled)
  * [with BrowserStack enabled](#with-browserstack-enabled)
  * [with TestingBot enabled](#with-testingbot-enabled)
  * [with screen width and height, and time zone](#with-screen-width-and-height-and-time-zone)
  * [with node folders mounted](#with-node-folders-mounted)
  * [configured to run more than one test per node](#configured-to-run-more-than-one-test-per-node)
  * [More configuration parameters](#more-configuration-parameters)
* [One line starters](#one-line-starters)
  * [Zalenium one-liner installer](#zalenium-one-liner-installer)
  * [Install and start](#install-and-start)
  * [Install and start a specific version](#install-and-start-a-specific-version)
  * [Cleanup](#cleanup)
* [Video feature](#video-feature)
* [Starting Zalenium with Docker Compose](#starting-zalenium-with-docker-compose)
* [Live preview](#live-preview)
  * [Displaying the live preview](#displaying-the-live-preview)
  * [Showing the test name on the live preview](#showing-the-test-name-on-the-live-preview)
  * [Filtering tests by build name](#filtering-tests-by-build-name)
* [Dashboard](#dashboard)
* [Test Configuration Options](#test-configuration-options)
  * [Test name](#test-name)
  * [Build name](#build-name)
  * [Idle Timeout](#idle-timeout)
  * [Screen resolution](#screen-resolution)
  * [Disable video recording](#disable-video-recording)
  * [Time zone](#time-zone)
  * [Marking the test as passed or failed](#marking-the-test-as-passed-or-failed)
  * [Referencing test steps in the recorded video](#referencing-test-steps-in-the-recorded-video)
* [Accessing the host](#accessing-the-host)
  * [Linux](#linux-env)
  * [OSX](#osx-env)
* [Adding hosts to the containers](#adding-hosts-to-the-containers)    
* [Enabling basic auth in Zalenium](#enabling-basic-auth-in-zalenium)  

## Initial setup

* Make sure your docker daemon is running
* Also double check that you have pulled the needed images:
  * `docker pull dosel/zalenium`
  * `docker pull elgalu/selenium`


## Starting Zalenium

Basic usage, without any of the integrated cloud testing platforms.
### Linux 

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      --privileged dosel/zalenium start 
  ```

### OSX 
Zalenium for OSX is currently compatible with Docker 1.11, 1.12 default and 1.13. In Mac is recommended that you 
explicitly tell Zalenium which major version you are using via `-e DOCKER=1.11` due to API compatibility issues. 

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -e DOCKER=1.11 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      --privileged dosel/zalenium start
  ```

### Windows 

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /c/Users/your_user_name/temp/videos:/home/seluser/videos \
      --privileged dosel/zalenium start      
  ```

### with Sauce Labs enabled

  ```sh
    export SAUCE_USERNAME=<your Sauce Labs username>
    export SAUCE_ACCESS_KEY=<your Sauce Labs access key>
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -e SAUCE_USERNAME -e SAUCE_ACCESS_KEY \
      -v /tmp/videos:/home/seluser/videos \
      -v /var/run/docker.sock:/var/run/docker.sock \
      --privileged dosel/zalenium start --sauceLabsEnabled true
  ```

### with BrowserStack enabled

  ```sh
    export BROWSER_STACK_USER=<your BrowserStack username>
    export BROWSER_STACK_KEY=<your BrowserStack access key>
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -e BROWSER_STACK_USER -e BROWSER_STACK_KEY \
      -v /tmp/videos:/home/seluser/videos \
      -v /var/run/docker.sock:/var/run/docker.sock \
      --privileged dosel/zalenium start --browserStackEnabled true
  ```

### with TestingBot enabled

  ```sh
    export TESTINGBOT_KEY=<your TestingBot access key>
    export TESTINGBOT_SECRET=<your TestingBot secret>
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -e TESTINGBOT_KEY -e TESTINGBOT_SECRET \
      -v /tmp/videos:/home/seluser/videos \
      -v /var/run/docker.sock:/var/run/docker.sock \
      --privileged dosel/zalenium start --testingBotEnabled true
  ```

### with screen width and height, and time zone

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      --privileged dosel/zalenium start --screenWidth 1440 --screenHeight 810 --timeZone "America/Montreal"
  ```

### with node folders mounted

This is a collection of folders that you can mount as volumes when starting Zalenium by prefixing the destination
with `/tmp/node/`, and it will be mapped across all the docker-selenium containers from the root folder after
stripping the `/tmp/node` prefix. 

For example, mounting:

`-v /your/local/folder:/tmp/node/home/seluser/folder` will map to `/home/seluser/folder` on the node. 
 
It can be used to provide further customization to your nodes, such as adding client certificates for your browser,
or mimicking prior multi-purpose folder, both shown below.

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      -v /your/local/folder/with/certStore:/tmp/node/home/seluser/.pki/nssdb \      
      -v /your/local/folderB:/tmp/node/home/seluser/folderB \      
      -v /tmp/mounted:/tmp/node/tmp/mounted \
      --privileged dosel/zalenium start 
  ```

Please take caution in mounting system folders such as `/etc`, as this behavior has not been tested with such configuration.

**NOTE:** There are certain protected points which cannot be mounted via `/tmp/node`. See
[PROTECTED_NODE_MOUNT_POINTS at DockerContainerClient](../src/main/java/de/zalando/ep/zalenium/container/DockerContainerClient.java).


### configured to run more than one test per node

By default, Zalenium will run only one test per node/container. This behaviour can be modified by using the flag
`--maxTestSessions`. If you setup this flag to a value higher than 1, Zalenium will run up to that given value of tests
per node/container. Tuning this value for your test suites should help to reduce the overall execution time since less
containers/nodes are started and stopped on demand. Here is an example:

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      --privileged dosel/zalenium start --maxTestSessions 4
  ```

This means that up to 4 tests will run in each node/container started by Zalenium. You could combine this parameter
with `--chromeContainers` or `--firefoxContainers` to get an optimal setup for your tests. For example, if you have
20 tests that should run in Chrome with 5 threads, you could start Zalenium with `--chromeContainers 5` and
`--maxTestSessions 4`. Therefore, 4 tests would be executed in each one of the 5 nodes/containers and the whole test
execution should finish earlier. 

### More configuration parameters

  * `--chromeContainers` -> Chrome nodes created on startup. Default is 1.
  * `--firefoxContainers` -> Firefox nodes created on startup. Default is 1.
  * `--maxDockerSeleniumContainers` -> Max number of docker-selenium containers running at the same time. Default is 10.
  * `--sauceLabsEnabled` -> Start Sauce Labs node or not. Defaults to 'false'.
  * `--browserStackEnabled` -> Start BrowserStack node or not. Defaults to 'false'.
  * `--testingbotEnabled` -> Start TestingBot node or not. Defaults to 'false'.
  * `--startTunnel` -> When a cloud testing platform is enabled, starts the tunnel to allow local testing. Defaults to 'false'.
  See the documentation on each provider on usage and any necessary Selenium capabilities. The local identifier used when creating
  the tunnel is `zalenium`. [Sauce Labs](https://wiki.saucelabs.com/display/DOCS/Using+Multiple+Sauce+Connect+Tunnels),
  [BrowserStack](https://www.browserstack.com/local-testing), [TestingBot](https://testingbot.com/support/other/tunnel)
  * `--videoRecordingEnabled` -> Sets if video is recorded in every test. Defaults to 'true'.
  * `--screenWidth` -> Sets the screen width. Defaults to 1900.
  * `--screenHeight` -> Sets the screen height. Defaults to 1880.
  * `--timeZone` -> Sets the time zone in the containers. Defaults to "Europe/Berlin".
  * `--debugEnabled` -> enables LogLevel.FINE. Defaults to 'false'.
  * `--seleniumImageName` -> enables overriding of the Docker selenium image to use. Defaults to 'elgalu/selenium'.
  * `--gridUser` -> allows you to specify a user to enable basic auth protection, `--gridPassword` must be provided also.
  * `--gridPassword` -> allows you to specify a user to enable basic auth protection, `--gridUser` must be provided also.



## One line starters

### Zalenium one-liner installer

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash
  ```
  
### Install and start

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s start
  ```

### Install and start a specific version

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s 3.0.1a start
  ```

### Cleanup

  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s stop
  ```

## Video feature
When you start Zalenium, and you map a host folder to `/home/seluser/videos`, it will copy all the generated videos 
from the executed tests into your host mapped folder.

For example, starting Zalenium like this

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      --privileged dosel/zalenium start 
  ```
  
will copy the generated videos to your local `/tmp/videos` folder. This means all videos generated from tests executed 
in docker-selenium containers and also from the ones executed in an integrated cloud testing platform (Sauce Labs, 
BrowserStack, TestingBot).

The file name will be usually like this:

  * Zalenium: `containerName_testName_browser_platform_timestamp.mp4`
    * `zalenium_myTestName_chrome_linux_20170216071201.mp4`
  * Cloud Testing Platform: `cloudPlatform_testName_browser_platform_timestamp.mp4/flv`
    * Sauce Labs: `saucelabs_myCloudTestName_safari_mac_20170216071201.flv` 
    * BrowserStack: `browserstack_myCloudTestName_firefox_windows_20170216071201.mp4`
  
If the test name is not set via a capability, the Selenium session ID will be used.

## Starting Zalenium with Docker Compose

You can see an example [here](./docker/docker-compose.yaml)

Beware that `docker-compose --abort-on-container-exit` renders the video unusable, the finalization of the file cannot happen. In this case, stopping Zalenium in case of the certain conditions must be automated in another way.

## Live preview

<p align="center">
  <img id="live-preview" src="../images/live_preview.gif" />
</p>

### Displaying the live preview
* Just go to [http://localhost:4444/grid/admin/live](http://localhost:4444/grid/admin/live)
  * You can also replace `localhost` for the IP/machine name where Zalenium is running
* Auto-refresh, add `?refresh=numberOfSeconds` to refresh the view automatically. E.g. 
[http://localhost:4444/grid/admin/live?refresh=20](http://localhost:4444/grid/admin/live?refresh=20) will refresh the 
page every 20 seconds.

### Showing the test name on the live preview
Having a `name` capability with the test name will display it in the live preview. See [test name](#test-name) for more 
information.

### Filtering tests by build name
If more than one person is using the same instance of Zalenium, with a `build` capability in your tests, the live 
preview can be filtered to show only the tests that belong to a specific build. Pass `?build=myTestBuild` at the end 
of the url. E.g. [http://localhost:4444/grid/admin/live?build=myTestBuild](http://localhost:4444/grid/admin/live?build=myTestBuild).
See more details at [build name](#build-name).

## Dashboard

<p align="center">
  <img id="dashboard" src="../images/dashboard.gif" />
</p>

* Go to [http://localhost:4444/dashboard](http://localhost:4444/dashboard)
  * You can also replace `localhost` for the IP/machine name where Zalenium is running
* Check all the recorded videos and aggregated logs after your tests completed
* Click on Cleanup to remove all videos and logs from the local drive and the dashboard
  * You can also cleanup the dashboard via `http://localhost:4444/dashboard/cleanup?action=doCleanupAll`

## Test Configuration Options

### Test name
Adding a `name` capability with the test name will do two things; it will be displayed in the live preview to help you
identify where your test is running, and the video file will also use it in the file name. 
Example code in Java for the capability:

  ```java
    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
    desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
    desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);
    desiredCapabilities.setCapability("name", "myTestName");
  ```

### Build name
Useful to filter the live preview and only display a group of tests belonging to the same build. Example code in Java
for the capability:

  ```java
    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
    desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
    desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);
    desiredCapabilities.setCapability("build", "myTestBuild");
  ```

### Idle timeout
By default, Zalenium allows a test to be idle up to 90 seconds. After that elapsed time, the session will be terminated, 
the node will be shutdown and the recorded video will be saved (if video recording is enabled). This prevents a test to 
run indefinitely after something went wrong. If you need to have a longer idle timeout, just set an `idleTimeout` 
capability in your test. Example code in Java for the capability (it sets the `idleTimeout` to 150 seconds):

  ```java
    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
    desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
    desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);
    desiredCapabilities.setCapability("idleTimeout", 150);
  ```

### Screen resolution
You can pass a custom screen resolution for your test, just include a `screenResolution` with the desired value. E.g. 
`screenResolution=1280x1024`. Also supported for the same purpose `resolution` and `screen-resolution`. Example code 
in Java for the capability `screenResolution`

  ```java
    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
    desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
    desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);
    desiredCapabilities.setCapability("screenResolution", "1280x720");
  ```

### Disable video recording
It is possible to disable video recording (enabled by default) via test capabilities. Add a `recordVideo=false` 
capability and no video will be recorded. Example code in Java for the capability `recordVideo`

  ```java
    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
    desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
    desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);
    desiredCapabilities.setCapability("recordVideo", false);
  ```

### Time zone
Run your test in a different time zone from the default one `Europe/Berlin`, just pass a capability `tz` with the 
desired value. E.g. `tz=America/Montreal`. Example code in Java for the capability `tz`

  ```java
    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
    desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
    desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);
    desiredCapabilities.setCapability("tz", "America/Montreal");
  ```

### Marking the test as passed or failed
By default, tests in Zalenium are marked in the dashboard either as COMPLETED (session finishes normally) or TIMEOUT
(session was ended due to inactivity). You can mark the test as passed or failed based on the assertions you do on
your side with your test framework, add a cookie from with the name `zaleniumTestPassed` with a value of `true` (if the
test passes) or false (if the test fails). This could be done in the after method where you already know if the test
passed or failed. Here is an example in Java: 

  ```java
    Cookie cookie = new Cookie("zaleniumTestPassed", "true");
    webDriver.manage().addCookie(cookie);
  ```

### Referencing test steps in the recorded video
It is possible to reference your tests steps in the recorded video by passing their description to Zalenium via a
cookie. For example, your test could go to the home page, search and add an article to the basket, go to the checkout,
and pay. All this steps can be referenced in the video for a more simple debugging. You can pass the steps via messages
with a cookie named `zaleniumMessage`. Here is an example in Java:

  ```java
    Cookie cookie = new Cookie("zaleniumMessage", "Go to home page");
    webDriver.manage().addCookie(cookie);
    webDriver.get("http://www.homepage.com");
    
    cookie = new Cookie("zaleniumMessage", "Search and add article to the basket");
    webDriver.manage().addCookie(cookie);
    /*
        Code performing WebDriver actions to search and add article to the basket.
     */

    cookie = new Cookie("zaleniumMessage", "Go to the checkout");
    webDriver.manage().addCookie(cookie);
    /*
        Code performing WebDriver actions to go to the checkout.
     */

    cookie = new Cookie("zaleniumMessage", "Pay");
    webDriver.manage().addCookie(cookie);
    /*
        Code performing WebDriver actions to pay.
     */

  ```

### Set browser language (works only with Chrome)
You can set the browser language when using Google Chrome, just pass the `ChromeOptions` variable with the language
argument. Example code in Java :

  ```java
    DesiredCapabilities desiredCapabilities = DesiredCapabilities.chrome();
    ChromeOptions options = new ChromeOptions();
    options.addArguments("lang=en_GB");
    desiredCapabilities.setCapability(ChromeOptions.CAPABILITY, options);
```

## Accessing the host
This is the scenario where you are running some tests with Zalenium, and the SUT (system under test) is running on your host machine.
Therefore, you want your tests to access your SUT.

### Linux Env
It is quite simple to access the host machine from Zalenium in a Linux environment. Just add the flag `--net=host` and it just works.
If your SUT is running on your machine on port 8080, you can do `http://localhost:8080` from inside the containers or in your tests.
Example:

With `docker run`:
  ```sh
    docker run --rm -ti --name zalenium --net=host \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      --privileged dosel/zalenium start 
  ```

With the one line starter:
  ```sh
    curl -sSL https://raw.githubusercontent.com/dosel/t/i/p | bash -s start --docker-opt '--net=host'
  ```

### OSX Env
In OSX environments the `--net=host` flag is not suported yet. For that, we have a workaround, which is to use `mac.host.local` to access
the host machine. So if the SUT is running on port 8080, you can do `http://mac.host.local:8080` to access it.


## Adding hosts to the containers
Sometimes you need to add host entries to the `/etc/hosts` file in order to mock dependencies, reach parts of your test infrastructure,
or just to simplify your test code. Zalenium supports the `--add-host` flag in `docker run ...` and the `extra_hosts` option in
docker-compose. You can see an example [here](./docker/docker-compose-extra-hosts.yaml)

## Enabling basic auth in Zalenium
Deploying Zalenium to a cloud provider (AWS, GCP, etc...)? You can enable the basich auth feature built in Nginx to protect Zalenium
when deploying it to the open internet. You can enable it in two different ways; providing a file with user(s) and password(s) or
using the parameters `--gridUser` and `--gridPassword`. Here are the detailed instructions:

### Providing a file with user(s) and password(s)
To create a file with that information, please follow the steps for "Creating a Password File"
[described in the Nginx documentation](https://www.nginx.com/resources/admin-guide/restricting-access-auth-basic/). After that, map the
created file to the container when you start Zalenium, e.g.:

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      -v $(pwd)/.htpasswd:/etc/nginx/.htpasswd
      --privileged dosel/zalenium start 
  ```

### Using the `--gridUser` and `--gridPassword` parameters

  ```sh
    docker run --rm -ti --name zalenium -p 4444:4444 \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /tmp/videos:/home/seluser/videos \
      --privileged dosel/zalenium start --gridUser yourUser --gridPassword yourPassword
  ```
 
### Using Zalenium when the basic auth is enabled
You will need to provide the user and the password stated in the file or in the parameters at the moment of running your tests. Here is
and example that shows you how to do it (the user will be `yourUser` and the password `yourPassword`).

```java

    @Test
    public void simpleGoogleTest() throws Exception {    
        /*
           NOTE THE USE OF "yourUser" and "yourPassword" in the RemoteWebDriver url.
        */
        String URL = "http://yourUser:yourPassword@localhost:4444/wd/hub";
        DesiredCapabilities desiredCapabilities = DesiredCapabilities.chrome();
        desiredCapabilities.setCapability(CapabilityType.PLATFORM, Platform.LINUX);

        // Create a new instance of the remote web driver
        WebDriver driver = new RemoteWebDriver(new URL(URL), desiredCapabilities);

        // Maximize the window
        driver.manage().window().maximize();

        // Go to Google
        driver.get("https://www.google.com");

        // Assert that the title is the expected one
        Assert.assertEquals(driver.getTitle(), "Google", "Page title is not the expected one");

        // Close the browser
        driver.quit();
    }
```



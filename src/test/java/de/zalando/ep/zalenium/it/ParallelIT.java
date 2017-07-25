package de.zalando.ep.zalenium.it;

import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;


@SuppressWarnings("UnusedParameters")
public class ParallelIT  {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelIT.class);

    // Zalenium setup variables
    private static final String DOCKER_SELENIUM_URL = "http://localhost:4444/wd/hub";

    // We need a thread safe environment to handle the webDriver variable in each thread separately
    private ThreadLocal<WebDriver> webDriver = new ThreadLocal<>();

    // Data provider which returns the browsers that will be used to run the tests
    @DataProvider(name = "browsersAndPlatforms")
    public static Object[][] browsersAndPlatformsProvider() {
        return new Object[][] {
                new Object[]{BrowserType.SAFARI, Platform.MAC},
                new Object[]{BrowserType.IE, Platform.WIN8},
                new Object[]{BrowserType.CHROME, Platform.LINUX},
                new Object[]{BrowserType.FIREFOX, Platform.ANY}
        };
    }

    // Data provider which returns the browsers that will be used to run the tests when the tunnel is on
    @DataProvider(name = "browsersAndPlatformsWithTunnel")
    public static Object[][] browsersAndPlatformsWithTunnelProvider() {
        return new Object[][] {
                new Object[]{BrowserType.CHROME, Platform.MAC, true},
                new Object[]{BrowserType.FIREFOX, Platform.WIN8, true}
        };
    }

    // Data provider which returns the browsers that will be used to run the tests
    @DataProvider(name = "browsersAndPlatformsForLivePreview")
    public static Object[][] browsersAndPlatformsForLivePreviewProvider() {
        return new Object[][] {
                new Object[]{BrowserType.CHROME, Platform.LINUX},
                new Object[]{BrowserType.FIREFOX, Platform.ANY},
        };
    }

    @BeforeMethod
    public void startWebDriverAndGetBaseUrl(Method method, Object[] testArgs) throws MalformedURLException {
        String browserType = testArgs[0].toString();
        Platform platform = (Platform) testArgs[1];
        boolean localTesting = false;
        if (testArgs.length > 2) {
            localTesting = testArgs[2] != null && (boolean) testArgs[2];
        }
        LOGGER.info("STARTING {} on {} - {}", method.getName(), browserType, platform.name());

        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, browserType);
        desiredCapabilities.setCapability(CapabilityType.PLATFORM, platform);
        desiredCapabilities.setCapability("name", method.getName());
        if (localTesting) {
            desiredCapabilities.setCapability("tunnel", "true");
            desiredCapabilities.setCapability("browserstack.local", "true");
            desiredCapabilities.setCapability("browserstack.localIdentifier", "zalenium");
            desiredCapabilities.setCapability("tunnelIdentifier", "zalenium");
        }

        try {
            webDriver.set(new RemoteWebDriver(new URL(DOCKER_SELENIUM_URL), desiredCapabilities));
        } catch (Exception e) {
            LOGGER.warn("FAILED {} on {} - {}", method.getName(), browserType, platform.name());
            throw e;
        }

    }

    @AfterMethod
    public void quitBrowser(Method method, Object[] testArgs) {
        webDriver.get().quit();
        String browserType = testArgs[0].toString();
        Platform platform = (Platform) testArgs[1];
        LOGGER.info("FINISHING {} on {} - {}", method.getName(), browserType, platform.name());
    }

    // Returns the webDriver for the current thread
    private WebDriver getWebDriver() {
        return webDriver.get();
    }

    @Test(dataProvider = "browsersAndPlatformsForLivePreview")
    public void checkIframeLinksForLivePreviewWithMachineIp(String browserType, Platform platform) {

        NetworkUtils networkUtils = new NetworkUtils();
        String hostIpAddress = networkUtils.getIp4NonLoopbackAddressOfThisMachine().getHostAddress();

        // Go to the homepage
        getWebDriver().get(String.format("http://%s:4444/grid/admin/live", hostIpAddress));

        // Get the page source to get the iFrame links
        String pageSource = getWebDriver().getPageSource();

        // Assert that the href for the iFrame has the vnc links
        assertThat(pageSource, containsString("/?view_only=true"));
        assertThat(pageSource, containsString("/?view_only=false"));
        // Assert that the href for the iFrame has the serverName from the request
        assertThat(pageSource, containsString(String.format("http://%s", hostIpAddress)));
    }


    @Test(dataProvider = "browsersAndPlatformsWithTunnel")
    public void loadSeleniumGridAndCheckTitle(String browserType, Platform platform, boolean local) {

        // Go to the homepage
        getWebDriver().get("http://localhost:4444/grid/console");

        // Assert that the title is the expected one
        assertThat(getWebDriver().getTitle(), equalTo("Grid Console"));
    }

    @Test(dataProvider = "browsersAndPlatforms")
    public void loadGooglePageAndCheckTitle(String browserType, Platform platform) {

        // Go to the homepage
        getWebDriver().get("http://www.google.com");

        // Assert that the title is the expected one
        assertThat(getWebDriver().getTitle(), containsString("Google"));
    }

}

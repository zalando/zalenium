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
import org.testng.util.Strings;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;


@SuppressWarnings("UnusedParameters")
public class ParallelIT  {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelIT.class);
    private static final String sauceLabsIntegration = "sauceLabs";
    private final String browserStackIntegration = "browserStack";
    private final String testingBotIntegration = "testingBot";
    private final String crossBrowserTestingIntegration = "crossBrowserTesting";

    // Zalenium setup variables
    private static final String ZALENIUM_HOST = System.getenv("ZALENIUM_GRID_HOST") != null ?
            System.getenv("ZALENIUM_GRID_HOST") : "localhost";
    private static final String ZALENIUM_PORT = System.getenv("ZALENIUM_GRID_PORT") != null ?
            System.getenv("ZALENIUM_GRID_PORT") : "4444";


    // We need a thread safe environment to handle the webDriver variable in each thread separately
    private ThreadLocal<WebDriver> webDriver = new ThreadLocal<>();

    // Data provider which returns the browsers that will be used to run the tests
    @DataProvider(name = "browsersAndPlatforms")
    public static Object[][] browsersAndPlatformsProvider() {
        String integrationToTest = System.getProperty("integrationToTest");
        if (!Strings.isNullOrEmpty(integrationToTest) && sauceLabsIntegration.equalsIgnoreCase(integrationToTest)) {
            return new Object[][] {
                    new Object[]{BrowserType.SAFARI, "macOS 10.14", "12"},
                    new Object[]{BrowserType.EDGE, "Windows 10", "18.17763"},
                    new Object[]{BrowserType.CHROME, Platform.LINUX},
                    new Object[]{BrowserType.FIREFOX, Platform.ANY}
            };
        }
        return new Object[][] {
                new Object[]{BrowserType.CHROME, Platform.LINUX},
                new Object[]{BrowserType.FIREFOX, Platform.ANY}
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

    @BeforeMethod(alwaysRun = true)
    public void startWebDriverAndGetBaseUrl(Method method, Object[] testArgs) throws MalformedURLException {
        String zaleniumUrl = String.format("http://%s:%s/wd/hub", ZALENIUM_HOST, ZALENIUM_PORT);
        String browserType = testArgs[0].toString();
        Platform platform = (Platform) testArgs[1];
        String version = "";
        if (testArgs.length > 2) {
            version = String.valueOf(testArgs[2]);
        }
        LOGGER.info("STARTING {} on {} - {}, using {}", method.getName(), browserType, platform.name(), zaleniumUrl);
        LOGGER.info("Integration to test {}", System.getProperty("integrationToTest"));

        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, browserType);
        desiredCapabilities.setCapability(CapabilityType.PLATFORM_NAME, platform);
        if (!Strings.isNotNullAndNotEmpty(version)) {
            desiredCapabilities.setCapability(CapabilityType.VERSION, version);
        }
        desiredCapabilities.setCapability("name", method.getName());

        try {
            webDriver.set(new RemoteWebDriver(new URL(zaleniumUrl), desiredCapabilities));
        } catch (Exception e) {
            LOGGER.warn("FAILED {} on {} - {}", method.getName(), browserType, platform.name());
            throw e;
        }

    }

    @AfterMethod(alwaysRun = true)
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
        getWebDriver().get(String.format("http://%s:%s/grid/admin/live", hostIpAddress, ZALENIUM_PORT));

        // Get the page source to get the iFrame links
        String pageSource = getWebDriver().getPageSource();

        // Assert that the href for the iFrame has the vnc links
        assertThat(pageSource, containsString("view_only=true"));
        assertThat(pageSource, containsString("view_only=false"));
    }


    @Test(dataProvider = "browsersAndPlatforms")
    public void loadGooglePageAndCheckTitle(String browserType, Platform platform) {

        // Go to the homepage
        getWebDriver().get("http://www.google.com");

        // Assert that the title is the expected one
        assertThat(getWebDriver().getTitle(), containsString("Google"));
    }

    @SuppressWarnings("groupsTestNG")
    @Test(dataProvider = "browsersAndPlatformsForLivePreview", groups = {"minikube"})
    public void loadTheInternetPageAndCheckTitle(String browserType, Platform platform) {

        // Go to the homepage
        getWebDriver().get("https://the-internet.herokuapp.com/");

        // Assert that the title is the expected one
        assertThat(getWebDriver().getTitle(), containsString("Internet"));
    }

}

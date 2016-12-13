package de.zalando.tip.zalenium.it;

import org.openqa.selenium.Platform;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

@SuppressWarnings("UnusedParameters")
public class ParallelIT  {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelIT.class);

    // Zalenium setup variables
    public static final String DOCKER_SELENIUM_URL = "http://localhost:4444/wd/hub";

    // We need a thread safe environment to handle the webDriver variable in each thread separately
    private ThreadLocal<WebDriver> webDriver = new ThreadLocal<>();

    // Data provider which returns the browsers that will be used to run the tests
    @DataProvider(name = "browsersAndPlatforms")
    public static Object[][] browsersAndPlatformsProvider() {
        return new Object[][] {
                new Object[]{BrowserType.CHROME, Platform.LINUX},
                new Object[]{BrowserType.FIREFOX, Platform.ANY},
                new Object[]{BrowserType.SAFARI, Platform.EL_CAPITAN},
                new Object[]{BrowserType.SAFARI, Platform.MAC},
                new Object[]{BrowserType.IE, Platform.WIN10}
        };
    }

    @BeforeMethod
    public void startWebDriverAndGetBaseUrl(Method method, Object[] testArgs) throws MalformedURLException {
        String browserType = testArgs[0].toString();
        Platform platform = (Platform) testArgs[1];
        LOGGER.info("STARTING {} on {} - {}", method.getName(), browserType, platform.name());

        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, browserType);
        desiredCapabilities.setCapability(CapabilityType.PLATFORM, platform);
        desiredCapabilities.setCapability("name", method.getName());

        try {
            webDriver.set(new RemoteWebDriver(new URL(DOCKER_SELENIUM_URL), desiredCapabilities));
        } catch (Exception e) {
            LOGGER.warn("FAILED {} on {} - {}", method.getName(), browserType, platform.name());
            throw e;
        }

        webDriver.get().manage().window().maximize();
    }

    @AfterMethod
    public void quitBrowser(Method method, Object[] testArgs) {
        webDriver.get().quit();
        String browserType = testArgs[0].toString();
        Platform platform = (Platform) testArgs[1];
        LOGGER.info("FINISHING {} on {} - {}", method.getName(), browserType, platform.name());
    }

    // Returns the webDriver for the current thread
    public WebDriver getWebDriver() {
        return webDriver.get();
    }

    @Test(dataProvider = "browsersAndPlatforms")
    public void loadZalandoPageAndCheckTitle(String browserType, Platform platform) {

        // Go to the homepage
        getWebDriver().get("http://www.zalando.de");

        // Assert that the title is the expected one
        Assert.assertEquals(getWebDriver().getTitle(), "Schuhe & Mode online kaufen | ZALANDO Online Shop",
                "Page title is not the expected one");
    }

    @Test(dataProvider = "browsersAndPlatforms")
    public void loadGooglePageAndCheckTitle(String browserType, Platform platform) {

        // Go to the homepage
        getWebDriver().get("http://www.google.com");

        // Assert that the title is the expected one
        Assert.assertEquals(getWebDriver().getTitle(), "Google", "Page title is not the expected one");
    }


}

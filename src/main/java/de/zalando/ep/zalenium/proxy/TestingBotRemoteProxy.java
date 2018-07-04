package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.selenium.remote.server.jmx.ManagedService;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
    This class should work in a similar way as its sibling, SauceLabsRemoteProxy
 */
@ManagedService(description = "TestingBot TestSlots")
public class TestingBotRemoteProxy extends CloudTestingRemoteProxy {

    private static final String TESTINGBOT_URL = getEnv().getStringEnvVariable("TESTINGBOT_URL", "http://hub.testingbot.com:80");
    private static final String TESTINGBOT_ACCOUNT_INFO = "https://api.testingbot.com/v1/user";
    private static final String TESTINGBOT_KEY = getEnv().getStringEnvVariable("TESTINGBOT_KEY", "");
    private static final String TESTINGBOT_SECRET = getEnv().getStringEnvVariable("TESTINGBOT_SECRET", "");
    private static final Logger logger = LoggerFactory.getLogger(TestingBotRemoteProxy.class.getName());
    private static final String TESTINGBOT_PROXY_NAME = "TestingBot";

    public TestingBotRemoteProxy(RegistrationRequest request, GridRegistry registry) {
        super(updateTBCapabilities(request, TESTINGBOT_ACCOUNT_INFO), registry);
    }

    @VisibleForTesting
    private static RegistrationRequest updateTBCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement testingBotAccountInfo = getCommonProxyUtilities().readJSONFromUrl(url, TESTINGBOT_KEY, TESTINGBOT_SECRET);
        try {
            registrationRequest.getConfiguration().capabilities.clear();
            String logMessage = String.format("[TB] Getting account max. concurrency from %s", url);
            int testingBotAccountConcurrency;
            if (testingBotAccountInfo == null) {
                logMessage = String.format("[TB] Account max. concurrency was NOT fetched from %s", url);
                testingBotAccountConcurrency = 1;
            } else {
                testingBotAccountConcurrency = testingBotAccountInfo.getAsJsonObject().get("max_concurrent").getAsInt();
            }
            logger.info(logMessage);
            return addCapabilitiesToRegistrationRequest(registrationRequest, testingBotAccountConcurrency,
                    TESTINGBOT_PROXY_NAME);
        } catch (Exception e) {
            logger.error(e.toString(), e);
            getGa().trackException(e);
        }
        return addCapabilitiesToRegistrationRequest(registrationRequest, 1, TESTINGBOT_PROXY_NAME);
    }

    @Override
    public String getUserNameProperty() {
        return "key";
    }

    @Override
    public String getUserNameValue() {
        return TESTINGBOT_KEY;
    }

    @Override
    public String getAccessKeyProperty() {
        return "secret";
    }

    @Override
    public String getAccessKeyValue() {
        return TESTINGBOT_SECRET;
    }

    @Override
    public String getCloudTestingServiceUrl() {
        return TESTINGBOT_URL;
    }

    @Override
    public boolean proxySupportsLatestAsCapability() {
        return true;
    }

    @Override
    public TestInformation getTestInformation(String seleniumSessionId) {
        // https://TB_KEY:TB_SECRET@api.testingbot.com/v1/tests/SELENIUM_SESSION_ID
        TestInformation testInformation = null;
        String testingBotTestUrl = "https://api.testingbot.com/v1/tests/%s";
        testingBotTestUrl = String.format(testingBotTestUrl, seleniumSessionId);
        for (int i = 0; i < 5; i++) {
            JsonObject testData = getCommonProxyUtilities().readJSONFromUrl(testingBotTestUrl, TESTINGBOT_KEY,
                    TESTINGBOT_SECRET).getAsJsonObject();
            String testName = testData.get("name").isJsonNull() ? null : testData.get("name").getAsString();
            String browser = testData.get("browser").getAsString();
            String browserVersion = testData.get("browser_version").getAsString();
            String platform = testData.get("os").getAsString();
            String videoUrl = testData.get("video").getAsString();
            List<String> logUrls = new ArrayList<>();
            testInformation = new TestInformation.TestInformationBuilder()
                    .withSeleniumSessionId(seleniumSessionId)
                    .withTestName(testName)
                    .withProxyName(getProxyName())
                    .withBrowser(browser)
                    .withBrowserVersion(browserVersion)
                    .withPlatform(platform)
                    .withTestStatus(TestInformation.TestStatus.COMPLETED)
                    .withFileExtension(getVideoFileExtension())
                    .withVideoUrl(videoUrl)
                    .withLogUrls(logUrls)
                    .withMetadata(getMetadata())
                    .build();
            // Sometimes the video URL is not ready right away, so we need to wait a bit and fetch again.
            if (videoUrl.startsWith("http")) {
                return testInformation;
            } else {
                try {
                    Thread.sleep(1000 * 5);
                } catch (InterruptedException e) {
                    logger.debug(e.toString(), e);
                }
            }
        }
        return testInformation;
    }


    @Override
    public String getVideoFileExtension() {
        return ".mp4";
    }

    @Override
    public String getProxyName() {
        return TESTINGBOT_PROXY_NAME;
    }

    @Override
    public String getProxyClassName() {
        return TestingBotRemoteProxy.class.getName();
    }

}

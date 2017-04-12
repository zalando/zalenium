package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zalando.tip.zalenium.util.TestInformation;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    This class should work in a similar way as its sibling, SauceLabsRemoteProxy
 */
public class TestingBotRemoteProxy extends CloudTestingRemoteProxy {

    private static final String TESTINGBOT_URL = "http://hub.testingbot.com:80";
    private static final String TESTINGBOT_CAPABILITIES_URL = "https://%s:%s@api.testingbot.com/v1/browsers";
    private static final Logger logger = Logger.getLogger(TestingBotRemoteProxy.class.getName());
    private static final String TESTINGBOT_CAPABILITIES_BK_FILE = "testingbot_capabilities.json";
    private static final String TESTINGBOT_KEY = getEnv().getStringEnvVariable("TESTINGBOT_KEY", "");
    private static final String TESTINGBOT_SECRET = getEnv().getStringEnvVariable("TESTINGBOT_SECRET", "");

    public TestingBotRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateTBCapabilities(request, String.format(TESTINGBOT_CAPABILITIES_URL, TESTINGBOT_KEY,
                TESTINGBOT_SECRET)), registry);
    }

    @VisibleForTesting
    private static RegistrationRequest updateTBCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement tbCapabilities = getCommonProxyUtilities().readJSONFromUrl(url);
        try {
            registrationRequest.getConfiguration().capabilities.clear();
            String userPasswordSuppress = String.format("%s:%s@", TESTINGBOT_KEY, TESTINGBOT_SECRET);
            String logMessage = String.format("[TB] Capabilities fetched from %s", url.replace(userPasswordSuppress, ""));
            if (tbCapabilities == null) {
                logMessage = String.format("[TB] Capabilities were NOT fetched from %s, loading from backup file",
                        url.replace(userPasswordSuppress, ""));
                tbCapabilities = getCommonProxyUtilities().readJSONFromFile(TESTINGBOT_CAPABILITIES_BK_FILE);
            }
            logger.log(Level.INFO, logMessage);
            return addCapabilitiesToRegistrationRequest(registrationRequest, tbCapabilities);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.toString(), e);
            getGa().trackException(e);
        }
        return registrationRequest;
    }

    private static RegistrationRequest addCapabilitiesToRegistrationRequest(RegistrationRequest registrationRequest, JsonElement tbCapabilities) {
        for (JsonElement cap : tbCapabilities.getAsJsonArray()) {
            JsonObject capAsJsonObject = cap.getAsJsonObject();
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
            desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 5);
            desiredCapabilities.setBrowserName(capAsJsonObject.get("name").getAsString());
            desiredCapabilities.setPlatform(Platform.extractFromSysProperty(capAsJsonObject.get("platform").getAsString()));
            if (!registrationRequest.getConfiguration().capabilities.contains(desiredCapabilities)) {
                registrationRequest.getConfiguration().capabilities.add(desiredCapabilities);
            }
        }
        return registrationRequest;
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
        String testingBotTestUrl = "https://%s:%s@api.testingbot.com/v1/tests/%s";
        testingBotTestUrl = String.format(testingBotTestUrl, getUserNameValue(), getAccessKeyValue(), seleniumSessionId);
        for (int i = 0; i < 5; i++) {
            JsonObject testData = getCommonProxyUtilities().readJSONFromUrl(testingBotTestUrl).getAsJsonObject();
            String testName = testData.get("name").getAsString();
            String browser = testData.get("browser").getAsString();
            String browserVersion = testData.get("browser_version").getAsString();
            String platform = testData.get("os").getAsString();
            String videoUrl = testData.get("video").getAsString();
            List<String> logUrls = new ArrayList<>();
            testInformation = new TestInformation(seleniumSessionId, testName, getProxyName(), browser, browserVersion,
                    platform, "", getVideoFileExtension(), videoUrl, logUrls);
            // Sometimes the video URL is not ready right away, so we need to wait a bit and fetch again.
            if (videoUrl.startsWith("http")) {
                return testInformation;
            } else {
                try {
                    Thread.sleep(1000 * 5);
                } catch (InterruptedException e) {
                    logger.log(Level.FINE, e.toString(), e);
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
        return "TestingBot";
    }

    @Override
    public String getProxyClassName() {
        return TestingBotRemoteProxy.class.getName();
    }

}

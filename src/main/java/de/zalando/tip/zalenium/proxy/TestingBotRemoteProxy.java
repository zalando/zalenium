package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.*;
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
    public TestSession getNewSession(Map<String, Object> requestedCapability) {
        logger.log(Level.INFO, "[TB] Test will be forwarded to TestingBot, {0}.", requestedCapability);
        return super.getNewSession(requestedCapability);
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
    public String getVideoUrl(String seleniumSessionId) {
        String testingBotVideoUrl = "https://s3-eu-west-1.amazonaws.com/eurectestingbot/%s.mp4";
        return String.format(testingBotVideoUrl, seleniumSessionId);
    }

    @Override
    public String getVideoFileExtension() {
        return ".mp4";
    }

    @Override
    public String getProxyName() {
        return "testingbot";
    }

}

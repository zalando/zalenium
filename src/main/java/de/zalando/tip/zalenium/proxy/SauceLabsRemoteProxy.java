package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    Almost all concepts and ideas for this part of the implementation are taken from the open source project seen here:
    https://github.com/rossrowe/sauce-grid-plugin
 */

public class SauceLabsRemoteProxy extends CloudTestingRemoteProxy {

    @VisibleForTesting
    static final String SAUCE_LABS_CAPABILITIES_URL = "http://saucelabs.com/rest/v1/info/platforms/webdriver";
    private static final String SAUCE_LABS_USER_NAME = System.getenv("SAUCE_USERNAME");
    private static final String SAUCE_LABS_ACCESS_KEY = System.getenv("SAUCE_ACCESS_KEY");
    private static final String SAUCE_LABS_URL = "http://ondemand.saucelabs.com:80";
    private static final Logger LOGGER = Logger.getLogger(SauceLabsRemoteProxy.class.getName());
    private static final String SAUCE_LABS_DEFAULT_CAPABILITIES_BK_FILE = "saucelabs_capabilities.json";

    public SauceLabsRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateSLCapabilities(request, SAUCE_LABS_CAPABILITIES_URL), registry);
    }

    @VisibleForTesting
    static RegistrationRequest updateSLCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement slCapabilities = getCommonProxyUtilities().readJSONFromUrl(url);

        try {
            registrationRequest.getConfiguration().capabilities.clear();
            String logMessage = String.format("[SL] Capabilities fetched from %s", url);
            if (slCapabilities == null) {
                logMessage = String.format("[SL] Capabilities were NOT fetched from %s, loading from backup file", url);
                slCapabilities = getCommonProxyUtilities().readJSONFromFile(SAUCE_LABS_DEFAULT_CAPABILITIES_BK_FILE);
            }
            LOGGER.log(Level.INFO, logMessage);
            return addCapabilitiesToRegistrationRequest(registrationRequest, slCapabilities);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString(), e);
            getGa().trackException(e);
        }
        return registrationRequest;
    }

    private static RegistrationRequest addCapabilitiesToRegistrationRequest(RegistrationRequest registrationRequest, JsonElement slCapabilities) {
        for (JsonElement cap : slCapabilities.getAsJsonArray()) {
            JsonObject capAsJsonObject = cap.getAsJsonObject();
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
            desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 5);
            desiredCapabilities.setBrowserName(capAsJsonObject.get("api_name").getAsString());
            desiredCapabilities.setPlatform(getPlatform(capAsJsonObject.get("os").getAsString()));
            if (!registrationRequest.getConfiguration().capabilities.contains(desiredCapabilities)) {
                registrationRequest.getConfiguration().capabilities.add(desiredCapabilities);
            }
        }
        return registrationRequest;
    }

    private static Platform getPlatform(String os) {
        if ("windows 2012".equalsIgnoreCase(os)) {
            return Platform.WIN8;
        }
        return Platform.extractFromSysProperty(os);
    }

    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {
        LOGGER.log(Level.INFO, "[SL] Test will be forwarded to Sauce Labs, {0}.", requestedCapability);
        return super.getNewSession(requestedCapability);
    }

    @Override
    public String getUserNameProperty() {
        return "username";
    }

    @Override
    public String getUserNameValue() {
        return SAUCE_LABS_USER_NAME;
    }

    @Override
    public String getAccessKeyProperty() {
        return "accessKey";
    }

    @Override
    public String getAccessKeyValue() {
        return SAUCE_LABS_ACCESS_KEY;
    }

    @Override
    public String getCloudTestingServiceUrl() {
        return SAUCE_LABS_URL;
    }

    @Override
    public String getVideoUrl(String seleniumSessionId) {
        String sauceLabsVideoUrl = "https://%s:%s@saucelabs.com/rest/v1/%s/jobs/%s/assets/video.flv";
        return String.format(sauceLabsVideoUrl, getUserNameValue(), getAccessKeyValue(), getUserNameValue(), seleniumSessionId);
    }

    @Override
    public String getVideoFileExtension() {
        return ".flv";
    }

    @Override
    public String getProxyName() {
        return "SauceLabs";
    }

}

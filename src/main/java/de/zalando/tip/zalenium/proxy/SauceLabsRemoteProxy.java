package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zalando.tip.zalenium.util.CommonProxyUtilities;
import de.zalando.tip.zalenium.util.GoogleAnalyticsApi;
import de.zalando.tip.zalenium.util.ZaleniumCapabilityMatcher;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    Almost all concepts and ideas for this part of the implementation are taken from the open source project seen here:
    https://github.com/rossrowe/sauce-grid-plugin
 */

public class SauceLabsRemoteProxy extends CloudTestingRemoteProxy {

    public static final String SAUCE_LABS_USER_NAME = System.getenv("SAUCE_USERNAME");
    public static final String SAUCE_LABS_ACCESS_KEY = System.getenv("SAUCE_ACCESS_KEY");
    public static final String SAUCE_LABS_URL = "http://ondemand.saucelabs.com:80";
    @VisibleForTesting
    protected static final String SAUCE_LABS_CAPABILITIES_URL = "http://saucelabs.com/rest/v1/info/platforms/webdriver";
    private static final Logger LOGGER = Logger.getLogger(SauceLabsRemoteProxy.class.getName());
    private static final String SAUCE_LABS_DEFAULT_CAPABILITIES_BK_FILE = "saucelabs_capabilities.json";
    private static final CommonProxyUtilities defaultCommonProxyUtilities = new CommonProxyUtilities();
    private static final GoogleAnalyticsApi defaultGA = new GoogleAnalyticsApi();
    private static CommonProxyUtilities commonProxyUtilities = defaultCommonProxyUtilities;
    private static GoogleAnalyticsApi ga = defaultGA;

    public SauceLabsRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateSLCapabilities(request, SAUCE_LABS_CAPABILITIES_URL), registry);
    }

    @VisibleForTesting
    protected static RegistrationRequest updateSLCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement slCapabilities = commonProxyUtilities.readJSONFromUrl(url);

        try {
            registrationRequest.getCapabilities().clear();
            if (slCapabilities != null) {
                LOGGER.log(Level.INFO, "[SL] Capabilities fetched from {0}", url);
            } else {
                LOGGER.log(Level.SEVERE, "[SL] Capabilities were NOT fetched from {0}, loading from backup file", url);
                slCapabilities = commonProxyUtilities.readJSONFromFile(SAUCE_LABS_DEFAULT_CAPABILITIES_BK_FILE);
            }
            return addCapabilitiesToRegistrationRequest(registrationRequest, slCapabilities);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString(), e);
            ga.trackException(e);
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
            if (!registrationRequest.getCapabilities().contains(desiredCapabilities)) {
                registrationRequest.addDesiredCapability(desiredCapabilities);
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

    @VisibleForTesting
    protected static void setCommonProxyUtilities(final CommonProxyUtilities utilities) {
        commonProxyUtilities = utilities;
    }

    @VisibleForTesting
    protected static void restoreCommonProxyUtilities() {
        commonProxyUtilities = defaultCommonProxyUtilities;
    }

    @VisibleForTesting
    protected static void restoreGa() {
        ga = defaultGA;
    }

    @VisibleForTesting
    protected static void setGa(GoogleAnalyticsApi ga) {
        SauceLabsRemoteProxy.ga = ga;
    }

    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {

        LOGGER.log(Level.INFO, "[SL] Test will be forwarded to Sauce Labs, {0}.", requestedCapability);

        return super.getNewSession(requestedCapability);
    }

    @Override
    String getUserNameProperty() {
        return "username";
    }

    @Override
    String getUserNameValue() {
        return SAUCE_LABS_USER_NAME;
    }

    @Override
    String getAccessKeyProperty() {
        return "accessKey";
    }

    @Override
    String getAccessKeyValue() {
        return SAUCE_LABS_ACCESS_KEY;
    }

    @Override
    public URL getRemoteHost() {
        try {
            return new URL(SAUCE_LABS_URL);
        } catch (MalformedURLException e) {
            LOGGER.log(Level.SEVERE, e.toString(), e);
            ga.trackException(e);
        }
        return null;
    }

}

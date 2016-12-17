package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zalando.tip.zalenium.util.CommonProxyUtilities;
import de.zalando.tip.zalenium.util.GoogleAnalyticsApi;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    This class should work in a similar way as its sibling, SauceLabsRemoteProxy
 */
public class BrowserStackRemoteProxy extends CloudTestingRemoteProxy {

    public static final String BROWSER_STACK_USER = System.getenv("BROWSER_STACK_USER");
    public static final String BROWSER_STACK_KEY = System.getenv("BROWSER_STACK_KEY");
    public static final String BROWSER_STACK_URL = "http://hub-cloud.browserstack.com:80";
    @VisibleForTesting
    protected static final String BROWSER_STACK_CAPABILITIES_URL = "https://%s:%s@www.browserstack.com/automate/browsers.json";
    private static final Logger logger = Logger.getLogger(BrowserStackRemoteProxy.class.getName());
    // TODO: Generate file and store it in the docker container
    private static final String BROWSER_STACK_CAPABILITIES_BK_FILE = "browserstack_capabilities.json";
    private static final CommonProxyUtilities defaultCommonProxyUtilities = new CommonProxyUtilities();
    private static final GoogleAnalyticsApi defaultGA = new GoogleAnalyticsApi();
    private static CommonProxyUtilities commonProxyUtilities = defaultCommonProxyUtilities;
    private static GoogleAnalyticsApi ga = defaultGA;
    private CapabilityMatcher capabilityHelper;

    public BrowserStackRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateBSCapabilities(request, String.format(BROWSER_STACK_CAPABILITIES_URL, BROWSER_STACK_USER, BROWSER_STACK_KEY)),
                registry);
    }

    @VisibleForTesting
    protected static RegistrationRequest updateBSCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement slCapabilities = commonProxyUtilities.readJSONFromUrl(url);

        try {
            registrationRequest.getCapabilities().clear();
            if (slCapabilities != null) {
                // TODO: Don't print the user and password (it is in the URL).
                logger.log(Level.INFO, "[BS] Capabilities fetched from {0}", url.replace(BROWSER_STACK_KEY, "")
                    .replace(BROWSER_STACK_USER, ""));
            } else {
                logger.log(Level.SEVERE, "[BS] Capabilities were NOT fetched from {0}, loading from backup file", url);
                slCapabilities = commonProxyUtilities.readJSONFromFile(BROWSER_STACK_CAPABILITIES_BK_FILE);
            }
            return addCapabilitiesToRegistrationRequest(registrationRequest, slCapabilities);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.toString(), e);
            ga.trackException(e);
        }
        return registrationRequest;
    }

    private static RegistrationRequest addCapabilitiesToRegistrationRequest(RegistrationRequest registrationRequest, JsonElement slCapabilities) {
        for (JsonElement cap : slCapabilities.getAsJsonArray()) {
            JsonObject capAsJsonObject = cap.getAsJsonObject();
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
            desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 5);
            String browser = capAsJsonObject.get("browser").getAsString();
            desiredCapabilities.setBrowserName(getBrowser(browser));
            String os = capAsJsonObject.get("os").getAsString();
            String osVersion = capAsJsonObject.get("os_version").getAsString();
            desiredCapabilities.setPlatform(getPlatform(os, osVersion));
            if (!registrationRequest.getCapabilities().contains(desiredCapabilities)) {
                registrationRequest.addDesiredCapability(desiredCapabilities);
            }
        }
        return registrationRequest;
    }

    private static String getBrowser(String browserName) {
        if ("ie".equalsIgnoreCase(browserName)) {
            return BrowserType.IE;
        }
        return browserName;
    }

    private static Platform getPlatform(String os, String osVersion) {
        if ("ios".equalsIgnoreCase(os)) {
            return Platform.MAC;
        }
        if ("windows".equalsIgnoreCase(os)) {
            if ("xp".equalsIgnoreCase(osVersion)) {
                return Platform.extractFromSysProperty(os);
            } else {
                return Platform.extractFromSysProperty(os + " " + osVersion);
            }
        }
        return Platform.extractFromSysProperty(osVersion);
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
        BrowserStackRemoteProxy.ga = ga;
    }

    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {

        logger.log(Level.INFO, "[BS] Test will be forwarded to Browser Stack, {0}.", requestedCapability);

        return super.getNewSession(requestedCapability);
    }

    @Override
    String getUserNameProperty() {
        return "browserstack.user";
    }

    @Override
    String getUserNameValue() {
        return BROWSER_STACK_USER;
    }

    @Override
    String getAccessKeyProperty() {
        return "browserstack.key";
    }

    @Override
    String getAccessKeyValue() {
        return BROWSER_STACK_KEY;
    }

    @Override
    public URL getRemoteHost() {
        try {
            return new URL(BROWSER_STACK_URL);
        } catch (MalformedURLException e) {
            logger.log(Level.SEVERE, e.toString(), e);
            ga.trackException(e);
        }
        return null;
    }
}

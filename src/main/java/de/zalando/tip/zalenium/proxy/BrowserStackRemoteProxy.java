package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    This class should work in a similar way as its sibling, SauceLabsRemoteProxy
 */
public class BrowserStackRemoteProxy extends CloudTestingRemoteProxy {

    private static final String BROWSER_STACK_URL = "http://hub-cloud.browserstack.com:80";
    private static final String BROWSER_STACK_CAPABILITIES_URL = "https://%s:%s@www.browserstack.com/automate/browsers.json";
    private static final Logger logger = Logger.getLogger(BrowserStackRemoteProxy.class.getName());
    private static final String BROWSER_STACK_CAPABILITIES_BK_FILE = "browserstack_capabilities.json";
    private static final String BROWSER_STACK_USER = getEnv().getStringEnvVariable("BROWSER_STACK_USER", "");
    private static final String BROWSER_STACK_KEY = getEnv().getStringEnvVariable("BROWSER_STACK_KEY", "");

    public BrowserStackRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateBSCapabilities(request, String.format(BROWSER_STACK_CAPABILITIES_URL, BROWSER_STACK_USER,
                BROWSER_STACK_KEY)), registry);
    }

    @VisibleForTesting
    private static RegistrationRequest updateBSCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement bsCapabilities = getCommonProxyUtilities().readJSONFromUrl(url);
        try {
            registrationRequest.getCapabilities().clear();
            String userPasswordSuppress = String.format("%s:%s@", BROWSER_STACK_USER, BROWSER_STACK_KEY);
            String logMessage = String.format("[BS] Capabilities fetched from %s", url.replace(userPasswordSuppress, ""));
            if (bsCapabilities == null) {
                logMessage = String.format("[BS] Capabilities were NOT fetched from %s, loading from backup file",
                        url.replace(userPasswordSuppress, ""));
                bsCapabilities = getCommonProxyUtilities().readJSONFromFile(BROWSER_STACK_CAPABILITIES_BK_FILE);
            }
            logger.log(Level.INFO, logMessage);
            return addCapabilitiesToRegistrationRequest(registrationRequest, bsCapabilities);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.toString(), e);
            getGa().trackException(e);
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
    String getCloudTestingServiceUrl() {
        return BROWSER_STACK_URL;
    }

}

package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.tip.zalenium.util.CommonProxyUtilities;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    Almost all concepts and ideas for this part of the implementation are taken from the open source project seen here:
    https://github.com/rossrowe/sauce-grid-plugin
 */

public class SauceLabsRemoteProxy extends DefaultRemoteProxy {

    private static final Logger LOGGER = Logger.getLogger(SauceLabsRemoteProxy.class.getName());
    private static final String SAUCE_LABS_DEFAULT_CAPABILITIES_BK_FILE = "saucelabs_capabilities.json";

    @VisibleForTesting
    protected static final String SAUCE_LABS_CAPABILITIES_URL = "http://saucelabs.com/rest/v1/info/platforms/webdriver";

    private static final CommonProxyUtilities defaultCommonProxyUtilities = new CommonProxyUtilities();
    private static CommonProxyUtilities commonProxyUtilities = defaultCommonProxyUtilities;

    public static final String SAUCE_LABS_USER_NAME = System.getenv("SAUCE_USERNAME");
    public static final String SAUCE_LABS_ACCESS_KEY = System.getenv("SAUCE_ACCESS_KEY");
    public static final String SAUCE_LABS_URL = "http://ondemand.saucelabs.com:80";

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
        }
        return registrationRequest;
    }

    private static RegistrationRequest addCapabilitiesToRegistrationRequest(RegistrationRequest registrationRequest, JsonElement slCapabilities) {
        for (JsonElement cap : slCapabilities.getAsJsonArray()) {
            JsonObject capAsJsonObject = cap.getAsJsonObject();
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
            desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 10);
            desiredCapabilities.setBrowserName(capAsJsonObject.get("api_name").getAsString());
            desiredCapabilities.setPlatform(getPlatform(capAsJsonObject.get("os").getAsString()));
            desiredCapabilities.setVersion(capAsJsonObject.get("long_version").getAsString());
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

    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {

        if (canDockerSeleniumProcessIt(requestedCapability)) {
            LOGGER.log(Level.FINE, "[SL] Capability supported by docker-selenium, should not be processed by Sauce Labs: " +
                    "{0}", requestedCapability);
            return null;
        }

        if (!hasCapability(requestedCapability)) {
            LOGGER.log(Level.FINE, "[SL] Capability not supported by Sauce Labs, rejecting it: {0}", requestedCapability);
            return null;
        }

        LOGGER.log(Level.INFO, "[SL] Test will be forwarded to Sauce Labs, {0}.", requestedCapability);

        return super.getNewSession(requestedCapability);
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (seleniumRequest.getRequestType().equals(RequestType.START_SESSION)) {
                String body = seleniumRequest.getBody();
                JsonObject jsonObject = new JsonParser().parse(body).getAsJsonObject();
                JsonObject desiredCapabilities = jsonObject.getAsJsonObject("desiredCapabilities");
                desiredCapabilities.addProperty("username", SAUCE_LABS_USER_NAME);
                desiredCapabilities.addProperty("accessKey", SAUCE_LABS_ACCESS_KEY);
                seleniumRequest.setBody(jsonObject.toString());
            }
        }
        super.beforeCommand(session, request, response);
    }

    public boolean canDockerSeleniumProcessIt(Map<String, Object> requestedCapability) {
        for (RemoteProxy remoteProxy : getRegistry().getAllProxies()) {
            if ((remoteProxy instanceof DockerSeleniumStarterRemoteProxy) &&
                    remoteProxy.hasCapability(requestedCapability)) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    protected static void setCommonProxyUtilities(final CommonProxyUtilities utilities) {
        commonProxyUtilities = utilities;
    }

    @VisibleForTesting
    protected static void restoreCommonProxyUtilities() {
        commonProxyUtilities = defaultCommonProxyUtilities;
    }

    /*
        Making the node seem as heavily used, in order to get it listed after the 'docker-selenium' nodes.
        99% used.
     */
    @Override
    public float getResourceUsageInPercent() {
        return 99;
    }

    @Override
    public URL getRemoteHost() {
        try {
            return new URL(SAUCE_LABS_URL);
        } catch (MalformedURLException e) {
            LOGGER.log(Level.SEVERE, e.toString(), e);
        }
        return null;
    }

}

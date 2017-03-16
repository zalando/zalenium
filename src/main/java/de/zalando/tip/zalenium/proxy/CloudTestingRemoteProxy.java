package de.zalando.tip.zalenium.proxy;

/*
    Many concepts and ideas are inspired from the open source project seen here:
    https://github.com/rossrowe/sauce-grid-plugin
 */

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.tip.zalenium.util.*;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.remote.CapabilityType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("WeakerAccess")
public class CloudTestingRemoteProxy extends DefaultRemoteProxy {

    private static final Logger logger = Logger.getLogger(CloudTestingRemoteProxy.class.getName());
    private static final GoogleAnalyticsApi defaultGA = new GoogleAnalyticsApi();
    private static final CommonProxyUtilities defaultCommonProxyUtilities = new CommonProxyUtilities();
    private static final Environment defaultEnvironment = new Environment();
    private static GoogleAnalyticsApi ga = defaultGA;
    private static CommonProxyUtilities commonProxyUtilities = defaultCommonProxyUtilities;
    private static Environment env = defaultEnvironment;
    private CapabilityMatcher capabilityHelper;

    @SuppressWarnings("WeakerAccess")
    public CloudTestingRemoteProxy(RegistrationRequest request, Registry registry) {
        super(request, registry);
    }

    protected static GoogleAnalyticsApi getGa() {
        return ga;
    }

    @VisibleForTesting
    static void setGa(GoogleAnalyticsApi ga) {
        CloudTestingRemoteProxy.ga = ga;
    }

    @VisibleForTesting
    static void restoreGa() {
        ga = defaultGA;
    }

    protected static CommonProxyUtilities getCommonProxyUtilities() {
        return commonProxyUtilities;
    }

    @VisibleForTesting
    static void setCommonProxyUtilities(final CommonProxyUtilities utilities) {
        commonProxyUtilities = utilities;
    }

    public static Environment getEnv() {
        return env;
    }

    @VisibleForTesting
    static void restoreCommonProxyUtilities() {
        commonProxyUtilities = defaultCommonProxyUtilities;
    }

    @VisibleForTesting
    static void restoreEnvironment() {
        env = defaultEnvironment;
    }

    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {
        logger.log(Level.INFO, () ->"Test will be forwarded to " + getProxyName() + ", " + requestedCapability);
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
                desiredCapabilities.addProperty(getUserNameProperty(), getUserNameValue());
                desiredCapabilities.addProperty(getAccessKeyProperty(), getAccessKeyValue());
                if (!desiredCapabilities.has(CapabilityType.VERSION) && proxySupportsLatestAsCapability()) {
                    desiredCapabilities.addProperty(CapabilityType.VERSION, "latest");
                }
                seleniumRequest.setBody(jsonObject.toString());
            }
        }
        super.beforeCommand(session, request, response);
    }

    @Override
    public void afterCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof WebDriverRequest && "DELETE".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (seleniumRequest.getRequestType().equals(RequestType.STOP_SESSION)) {
                long executionTime = (System.currentTimeMillis() - session.getSlot().getLastSessionStart()) / 1000;
                getGa().testEvent(CloudTestingRemoteProxy.class.getName(), session.getRequestedCapabilities().toString(),
                        executionTime);
                addTestToDashboard(session.getExternalKey().getKey());
            }
        }
        super.afterCommand(session, request, response);
    }

    public String getUserNameProperty() {
        return null;
    }

    public String getUserNameValue() {
        return null;
    }

    public String getAccessKeyProperty() {
        return null;
    }

    public String getAccessKeyValue() {
        return null;
    }

    public String getCloudTestingServiceUrl() {
        return null;
    }

    public TestInformation getTestInformation(String seleniumSessionId) {
        return null;
    }

    public String getProxyName() {
        return null;
    }

    public String getVideoFileExtension() {
        return null;
    }

    public boolean proxySupportsLatestAsCapability() {
        return false;
    }

    public void addTestToDashboard(String seleniumSessionId) {
        new Thread(() -> {
            try {
                TestInformation testInformation = getTestInformation(seleniumSessionId);
                String videoFileNameWithFullPath = testInformation.getVideoFolderPath() + "/" + testInformation.getFileName();
                commonProxyUtilities.downloadFile(testInformation);
                Dashboard.updateDashboard(testInformation);
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.toString(), e);
            }
        }).start();
    }

    @Override
    public CapabilityMatcher getCapabilityHelper() {
        if (capabilityHelper == null) {
            capabilityHelper = new ZaleniumCapabilityMatcher(this);
        }
        return capabilityHelper;
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
            return new URL(getCloudTestingServiceUrl());
        } catch (MalformedURLException e) {
            logger.log(Level.SEVERE, e.toString(), e);
            getGa().trackException(e);
        }
        return null;
    }

}

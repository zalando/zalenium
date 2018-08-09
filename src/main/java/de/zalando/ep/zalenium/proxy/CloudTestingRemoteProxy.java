package de.zalando.ep.zalenium.proxy;

/*
    Many concepts and ideas are inspired from the open source project seen here:
    https://github.com/rossrowe/sauce-grid-plugin
 */

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.ep.zalenium.dashboard.DashboardCollection;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityMatcher;
import de.zalando.ep.zalenium.servlet.renderer.CloudProxyHtmlRenderer;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import org.apache.commons.io.FileUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.SessionTerminationReason;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.server.jmx.ManagedService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Optional;

@SuppressWarnings("WeakerAccess")
@ManagedService(description = "CloudTesting TestSlots")
public class CloudTestingRemoteProxy extends DefaultRemoteProxy {

    @VisibleForTesting
    public static final long DEFAULT_MAX_TEST_IDLE_TIME_SECS = 90L;
    @VisibleForTesting
    public static boolean addToDashboardCalled = false;
    private static final Logger logger = LoggerFactory.getLogger(CloudTestingRemoteProxy.class.getName());
    private static final GoogleAnalyticsApi defaultGA = new GoogleAnalyticsApi();
    private static final CommonProxyUtilities defaultCommonProxyUtilities = new CommonProxyUtilities();
    private static final Environment defaultEnvironment = new Environment();
    private static GoogleAnalyticsApi ga = defaultGA;
    private static CommonProxyUtilities commonProxyUtilities = defaultCommonProxyUtilities;
    private static Environment env = defaultEnvironment;
    private final HtmlRenderer renderer = new CloudProxyHtmlRenderer(this);
    private CloudProxyNodePoller cloudProxyNodePoller = null;
    private CapabilityMatcher capabilityHelper;
    private long maxTestIdleTime = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
    private JsonObject metadata;

    @SuppressWarnings("WeakerAccess")
    public CloudTestingRemoteProxy(RegistrationRequest request, GridRegistry registry) {
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
    public static void setCommonProxyUtilities(final CommonProxyUtilities utilities) {
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

    public static RegistrationRequest addCapabilitiesToRegistrationRequest(RegistrationRequest registrationRequest,
                                                                           int concurrency, String proxyName) {
        MutableCapabilities desiredCapabilities = new MutableCapabilities();
        desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, concurrency);
        desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, proxyName);
        desiredCapabilities.setCapability(CapabilityType.PLATFORM_NAME, Platform.ANY);
        registrationRequest.getConfiguration().capabilities.add(desiredCapabilities);
        registrationRequest.getConfiguration().maxSession = concurrency;
        return registrationRequest;
    }

    public long getMaxTestIdleTime() {
        return maxTestIdleTime;
    }

    @SuppressWarnings("SameParameterValue")
    @VisibleForTesting
    public void setMaxTestIdleTime(long maxTestIdleTime) {
        this.maxTestIdleTime = maxTestIdleTime;
    }

    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {
        /*
            Validate first if the capability is matched
        */
        if (!hasCapability(requestedCapability)) {
            return null;
        }

        logger.info("Test will be forwarded to " + getProxyName() + ", " + requestedCapability);
        return super.getNewSession(requestedCapability);
    }


    public JsonObject getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonObject metadata) {
        this.metadata = metadata;
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (seleniumRequest.getRequestType().equals(RequestType.START_SESSION)) {
                int numberOfParallelCloudSessions = getNumberOfSessions();
                MDC.put("numberOfParallelCloudSessions",String.valueOf(numberOfParallelCloudSessions));
                logger.info("Currently using " + numberOfParallelCloudSessions + " parallel sessions towards " + getProxyName() + ". Attempt to start one more.");
                MDC.clear();

                String body = seleniumRequest.getBody();
                JsonObject jsonObject = new JsonParser().parse(body).getAsJsonObject();
                JsonObject desiredCapabilities = jsonObject.getAsJsonObject("desiredCapabilities");
                desiredCapabilities.addProperty(getUserNameProperty(), getUserNameValue());
                desiredCapabilities.addProperty(getAccessKeyProperty(), getAccessKeyValue());
                if (!desiredCapabilities.has(CapabilityType.VERSION) && proxySupportsLatestAsCapability()) {
                    desiredCapabilities.addProperty(CapabilityType.VERSION, "latest");
                }
                try {
                    seleniumRequest.setBody(jsonObject.toString());
                } catch (UnsupportedEncodingException e) {
                    logger.error("Error while setting the body request in " + getProxyName()
                            + ", " + jsonObject.toString());
                }
            }

            if (seleniumRequest.getPathInfo() != null && seleniumRequest.getPathInfo().endsWith("cookie")) {
                logger.info(getId() + " Checking for cookies..." + seleniumRequest.getBody());
                JsonElement bodyRequest = new JsonParser().parse(seleniumRequest.getBody());
                JsonObject cookie = bodyRequest.getAsJsonObject().getAsJsonObject("cookie");
                JsonObject emptyName = new JsonObject();
                emptyName.addProperty("name", "");
                String cookieName = Optional.ofNullable(cookie.get("name")).orElse(emptyName.get("name")).getAsString();
                if(CommonProxyUtilities.metadataCookieName.equalsIgnoreCase(cookieName)) {
                    JsonObject metadata = new JsonParser().parse(cookie.get("value").getAsString()).getAsJsonObject();
                    this.setMetadata(metadata);
                }
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
                getGa().testEvent(getProxyClassName(), session.getRequestedCapabilities().toString(),
                        executionTime);
                addTestToDashboard(session.getExternalKey().getKey(), true);
            }
        }
        super.afterCommand(session, request, response);
    }

    @Override
    public HtmlRenderer getHtmlRender() {
        return this.renderer;
    }

    public String getProxyClassName() {
        return null;
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
        return "http://localhost:4444";
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

    public boolean useAuthenticationToDownloadFile() {
        return false;
    }

    public void addTestToDashboard(String seleniumSessionId, boolean testCompleted) {
        addToDashboardCalled = false;
        new Thread(() -> {
            try {
                TestInformation testInformation = getTestInformation(seleniumSessionId);
                TestInformation.TestStatus status = testCompleted ?
                        TestInformation.TestStatus.COMPLETED : TestInformation.TestStatus.TIMEOUT;
                testInformation.setTestStatus(status);
                String fileNameWithFullPath = testInformation.getVideoFolderPath() + "/" + testInformation.getFileName();
                commonProxyUtilities.downloadFile(testInformation.getVideoUrl(), fileNameWithFullPath,
                        getUserNameValue(), getAccessKeyValue(), useAuthenticationToDownloadFile());
                for (String logUrl : testInformation.getLogUrls()) {
                    String fileName = logUrl.substring(logUrl.lastIndexOf('/') + 1);
                    fileNameWithFullPath = testInformation.getLogsFolderPath() + "/" + fileName;
                    commonProxyUtilities.downloadFile(logUrl, fileNameWithFullPath,
                            getUserNameValue(), getAccessKeyValue(), useAuthenticationToDownloadFile());
                }
                createFeatureNotImplementedFile(testInformation.getLogsFolderPath());
                DashboardCollection.updateDashboard(testInformation);
                addToDashboardCalled = true;
            } catch (Exception e) {
                logger.error(e.toString(), e);
            }
        }, "CloudTestingRemoteProxy addTestToDashboard seleniumSessionId [" + seleniumSessionId + "] testCompleted ["
                + testCompleted + "]").start();
    }

    @Override
    public CapabilityMatcher getCapabilityHelper() {
        if (capabilityHelper == null) {
            capabilityHelper = new ZaleniumCapabilityMatcher();
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
            logger.error(e.toString(), e);
            getGa().trackException(e);
        }
        return null;
    }

    private void createFeatureNotImplementedFile(String logsFolderPath) {
        String fileNameWithFullPath = logsFolderPath + "/not_implemented.log";
        File notImplemented = new File(fileNameWithFullPath);
        try {
            String textToWrite = String.format("Feature not implemented for %s, we are happy to receive PRs", getProxyName());
            FileUtils.writeStringToFile(notImplemented, textToWrite, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.info(e.toString(), e);
        }

    }

    @Override
    public void startPolling() {
        super.startPolling();
        cloudProxyNodePoller = new CloudProxyNodePoller(this);
        cloudProxyNodePoller.start();
    }

    @Override
    public void stopPolling() {
        super.stopPolling();
        cloudProxyNodePoller.interrupt();
    }

    @Override
    public void teardown() {
        super.teardown();
        stopPolling();
    }

    /*
      Method to check for number of active Test Sessions
    */
    @VisibleForTesting
    public int getNumberOfSessions() {
      int numberOfSessions = 0;
      for (TestSlot testSlot : getTestSlots()) {
        if(testSlot.getSession() != null){
          numberOfSessions++;
        }
      }
      return numberOfSessions;
    }

    /*
        Method to check for test inactivity, and terminate idle sessions
     */
    @VisibleForTesting
    public void terminateIdleSessions() {
        for (TestSlot testSlot : getTestSlots()) {
            if (testSlot.getSession() != null &&
                    (testSlot.getSession().getInactivityTime() >= (getMaxTestIdleTime() * 1000L))) {
                long executionTime = (System.currentTimeMillis() - testSlot.getLastSessionStart()) / 1000;
                getGa().testEvent(getProxyClassName(), testSlot.getSession().getRequestedCapabilities().toString(),
                        executionTime);
                // If it is null, it is probable that the test never reached the cloud service.
                if (testSlot.getSession().getExternalKey() != null) {
                    addTestToDashboard(testSlot.getSession().getExternalKey().getKey(), false);
                }
                getRegistry().forceRelease(testSlot, SessionTerminationReason.ORPHAN);
                logger.info(getProxyName() + " Releasing slot and terminating session due to inactivity.");
            }
        }
    }

    /*
        Class to poll continuously the slots status to check if there is an idle test. It could happen that the test
        did not finish properly so we need to release the slot as well.
    */
    static class CloudProxyNodePoller extends Thread {

        private static long sleepTimeBetweenChecks = 500;
        private CloudTestingRemoteProxy cloudProxy;

        CloudProxyNodePoller(CloudTestingRemoteProxy cloudProxy) {
            this.cloudProxy = cloudProxy;
        }

        protected long getSleepTimeBetweenChecks() {
            return sleepTimeBetweenChecks;
        }

        @Override
        public void run() {
            while (true) {
                /*
                    Checking continuously for idle sessions. It may happen that the session is terminated abnormally
                    remotely and the slot needs to be released locally as well.
                */
                cloudProxy.terminateIdleSessions();
                try {
                    Thread.sleep(getSleepTimeBetweenChecks());
                } catch (InterruptedException e) {
                    logger.debug(cloudProxy.getProxyName() + " Error while sleeping the thread.", e);
                    return;
                }
            }
        }
    }

}

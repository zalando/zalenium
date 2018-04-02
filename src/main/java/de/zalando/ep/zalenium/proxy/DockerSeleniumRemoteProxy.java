package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.matcher.DockerSeleniumCapabilityMatcher;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.RemoteUnregisterException;
import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.SessionTerminationReason;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.console.DefaultProxyHtmlRenderer;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.server.jmx.ManagedService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
    The implementation of this class was inspired on https://gist.github.com/krmahadevan/4649607
 */
@SuppressWarnings("WeakerAccess")
@ManagedService(description = "DockerSelenium TestSlots")
public class DockerSeleniumRemoteProxy extends DefaultRemoteProxy {

    @VisibleForTesting
    public static final String ZALENIUM_MAX_TEST_SESSIONS = "ZALENIUM_MAX_TEST_SESSIONS";
    @VisibleForTesting
    public static final long DEFAULT_MAX_TEST_IDLE_TIME_SECS = 90L;
    @VisibleForTesting
    public static final String ZALENIUM_VIDEO_RECORDING_ENABLED = "ZALENIUM_VIDEO_RECORDING_ENABLED";
    @VisibleForTesting
    public static final boolean DEFAULT_VIDEO_RECORDING_ENABLED = true;
    private static final String ZALENIUM_KEEP_ONLY_FAILED_TESTS = "ZALENIUM_KEEP_ONLY_FAILED_TESTS";
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerSeleniumRemoteProxy.class.getName());
    private static final int DEFAULT_MAX_TEST_SESSIONS = 1;
    private static final boolean DEFAULT_KEEP_ONLY_FAILED_TESTS = false;
    private static final Environment defaultEnvironment = new Environment();
    private static int maxTestSessions;
    private static boolean keepOnlyFailedTests;
    private static boolean videoRecordingEnabledGlobal;
    private static Environment env = defaultEnvironment;
    private final HtmlRenderer renderer = new DefaultProxyHtmlRenderer(this);
    private final ContainerClientRegistration registration;
    private boolean videoRecordingEnabledSession;
    private boolean videoRecordingEnabledConfigured = false;
    private boolean cleaningUpBeforeNextSession;
    private ContainerClient containerClient = ContainerFactory.getContainerClient();
    private int amountOfExecutedTests;
    private long maxTestIdleTimeSecs;
    private String testBuild;
    private String testName;
    private TestInformation testInformation;
    private DockerSeleniumNodePoller dockerSeleniumNodePollerThread = null;
    private GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private CapabilityMatcher capabilityHelper;

    public DockerSeleniumRemoteProxy(RegistrationRequest request, GridRegistry registry) {
        super(request, registry);
        this.amountOfExecutedTests = 0;
        readEnvVars();
        containerClient.setNodeId(getId());
        registration = containerClient.registerNode(DockerSeleniumStarterRemoteProxy.getContainerName(), this.getRemoteHost());
    }

    @VisibleForTesting
    static void readEnvVars() {
        boolean videoEnabled = env.getBooleanEnvVariable(ZALENIUM_VIDEO_RECORDING_ENABLED,
                DEFAULT_VIDEO_RECORDING_ENABLED);
        setVideoRecordingEnabledGlobal(videoEnabled);

        maxTestSessions = env.getIntEnvVariable(ZALENIUM_MAX_TEST_SESSIONS, DEFAULT_MAX_TEST_SESSIONS);
        keepOnlyFailedTests = env.getBooleanEnvVariable(ZALENIUM_KEEP_ONLY_FAILED_TESTS,
                DEFAULT_KEEP_ONLY_FAILED_TESTS);
    }

    @VisibleForTesting
    protected static void setEnv(final Environment env) {
        DockerSeleniumRemoteProxy.env = env;
    }

    @VisibleForTesting
    static void restoreEnvironment() {
        env = defaultEnvironment;
    }

    private static void setVideoRecordingEnabledGlobal(boolean videoRecordingEnabled) {
        DockerSeleniumRemoteProxy.videoRecordingEnabledGlobal = videoRecordingEnabled;
    }

    @VisibleForTesting
    protected boolean isVideoRecordingEnabled() {
        if (this.videoRecordingEnabledConfigured) {
            return this.videoRecordingEnabledSession;
        }
        return DockerSeleniumRemoteProxy.videoRecordingEnabledGlobal;
    }

    private void setVideoRecordingEnabledSession(boolean videoRecordingEnabled) {
        this.videoRecordingEnabledSession = videoRecordingEnabled;
        this.videoRecordingEnabledConfigured = true;
    }

    @VisibleForTesting
    void setContainerClient(final ContainerClient client) {
        containerClient = client;
    }

    @VisibleForTesting
    void restoreContainerClient() {
        containerClient = ContainerFactory.getContainerClient();
    }

    public HtmlRenderer getHtmlRender() {
        return this.renderer;
    }

    /*
        Incrementing the number of tests that will be executed when the session is assigned.
     */
    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {
        /*
            Validate first if the capability is matched
         */
        if (!hasCapability(requestedCapability)) {
            return null;
        }

        if (!requestedCapability.containsKey(CapabilityType.BROWSER_NAME)) {
            LOGGER.debug(String.format("%s Capability %s does not contain %s key, a browser test cannot " +
                            "start without it.", getId(), requestedCapability, CapabilityType.BROWSER_NAME));
            return null;
        }

        if (!this.isBusy() && increaseCounter()) {
            TestSession newSession = super.getNewSession(requestedCapability);
            LOGGER.debug(getId() + " Creating session for: " + requestedCapability.toString());
            String browserName = requestedCapability.get(CapabilityType.BROWSER_NAME).toString();
            testName = getCapability(requestedCapability, ZaleniumCapabilityType.TEST_NAME, "");
            if (testName.isEmpty()) {
                testName = newSession.getExternalKey() != null ?
                        newSession.getExternalKey().getKey() :
                        newSession.getInternalKey();
            }
            testBuild = getCapability(requestedCapability, ZaleniumCapabilityType.BUILD_NAME, "");
            if (requestedCapability.containsKey(ZaleniumCapabilityType.RECORD_VIDEO)) {
                boolean videoRecording = Boolean.parseBoolean(getCapability(requestedCapability, ZaleniumCapabilityType.RECORD_VIDEO, "true"));
                setVideoRecordingEnabledSession(videoRecording);
            }
            String screenResolution = getCapability(newSession.getSlot().getCapabilities(), ZaleniumCapabilityType.SCREEN_RESOLUTION, "N/A");
            String browserVersion = getCapability(newSession.getSlot().getCapabilities(), CapabilityType.VERSION, "");
            String timeZone = getCapability(newSession.getSlot().getCapabilities(), ZaleniumCapabilityType.TIME_ZONE, "N/A");
            testInformation = new TestInformation.TestInformationBuilder()
                    .withTestName(testName)
                    .withSeleniumSessionId(testName)
                    .withProxyName("Zalenium")
                    .withBrowser(browserName)
                    .withBrowserVersion(browserVersion)
                    .withPlatform(Platform.LINUX.name())
                    .withScreenDimension(screenResolution)
                    .withTimeZone(timeZone)
                    .withBuild(testBuild)
                    .withTestStatus(TestInformation.TestStatus.COMPLETED)
                    .build();
            testInformation.setVideoRecorded(isVideoRecordingEnabled());
            maxTestIdleTimeSecs = getConfiguredIdleTimeout(requestedCapability);
            return newSession;
        }
        LOGGER.debug(String.format("%s No more sessions allowed", getId()));
        return null;
    }

    @Override
    public CapabilityMatcher getCapabilityHelper() {
        if (capabilityHelper == null) {
            capabilityHelper = new DockerSeleniumCapabilityMatcher(this);
        }
        return capabilityHelper;
    }

    private long getConfiguredIdleTimeout(Map<String, Object> requestedCapability) {
        long configuredIdleTimeout;
        try {
            Object idleTimeout = requestedCapability.getOrDefault(ZaleniumCapabilityType.IDLE_TIMEOUT, DEFAULT_MAX_TEST_IDLE_TIME_SECS);
            configuredIdleTimeout = Long.valueOf(String.valueOf(idleTimeout));
        } catch (Exception e) {
            configuredIdleTimeout = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
            LOGGER.warn(getId() + " " + e.toString());
            LOGGER.debug(getId() + " " + e.toString(), e);
        }
        if (configuredIdleTimeout <= 0) {
            configuredIdleTimeout = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
        }
        return configuredIdleTimeout;
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        super.beforeCommand(session, request, response);
        LOGGER.debug(getId() + " lastCommand: " +  request.getMethod() + " - " + request.getPathInfo() + " executing...");
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (seleniumRequest.getPathInfo().endsWith("cookie")) {
                LOGGER.debug(getId() + " Checking for cookies..." + seleniumRequest.getBody());
                JsonElement bodyRequest = new JsonParser().parse(seleniumRequest.getBody());
                JsonObject cookie = bodyRequest.getAsJsonObject().getAsJsonObject("cookie");
                JsonObject emptyName = new JsonObject();
                emptyName.addProperty("name", "");
                String cookieName = Optional.ofNullable(cookie.get("name")).orElse(emptyName.get("name")).getAsString();
                if ("zaleniumTestPassed".equalsIgnoreCase(cookieName)) {
                    boolean testPassed = Boolean.parseBoolean(cookie.get("value").getAsString());
                    if (testPassed) {
                        testInformation.setTestStatus(TestInformation.TestStatus.SUCCESS);
                    } else {
                        testInformation.setTestStatus(TestInformation.TestStatus.FAILED);
                    }
                }
                if ("zaleniumMessage".equalsIgnoreCase(cookieName)) {
                    String message = cookie.get("value").getAsString();
                    String messageCommand = String.format(" 'Zalenium', '%s', --icon=/home/seluser/images/message.png",
                            message);
                    processContainerAction(DockerSeleniumContainerAction.CLEAN_NOTIFICATION, getContainerId());
                    processContainerAction(DockerSeleniumContainerAction.SEND_NOTIFICATION, messageCommand,
                            getContainerId());
                }
            }
        }
    }

    @Override
    public void afterCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        super.afterCommand(session, request, response);
        LOGGER.debug(getId() + " lastCommand: " +  request.getMethod() + " - " + request.getPathInfo() + " executed.");
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (RequestType.START_SESSION.equals(seleniumRequest.getRequestType())) {
                
                String remoteName = "";
                if (session.getSlot().getProxy() instanceof DockerSeleniumRemoteProxy) {
                    remoteName = ((DockerSeleniumRemoteProxy)session.getSlot().getProxy()).getRegistration().getContainerId();
                }
                ExternalSessionKey externalKey = Optional.ofNullable(session.getExternalKey()).orElse(new ExternalSessionKey("[No external key present]"));
                LOGGER.info(String.format("Test session started with internal key %s and external key %s assigned to remote %s.",
                              session.getInternalKey(),
                              externalKey.getKey(),
                              remoteName));
                videoRecording(DockerSeleniumContainerAction.START_RECORDING);
            }
        }
    }

    @Override
    public void afterSession(TestSession session) {
        try {
            // This means that the shutdown command was triggered before receiving this afterSession command
            if (!TestInformation.TestStatus.TIMEOUT.equals(testInformation.getTestStatus())) {
                long executionTime = (System.currentTimeMillis() - session.getSlot().getLastSessionStart()) / 1000;
                ga.testEvent(DockerSeleniumRemoteProxy.class.getName(), session.getRequestedCapabilities().toString(),
                        executionTime);
                // This avoids shutting down the node by timeout in case the file copying takes too long.
                stopPolling();
                if (isTestSessionLimitReached()) {
                    String message = String.format("%s AFTER_SESSION command received. Node should shutdown soon...", getId());
                    LOGGER.info(message);
                    shutdownNode(false);
                }
                else {
                    String message = String.format(
                            "%s AFTER_SESSION command received. Cleaning up node for reuse, used %s of max %s", getId(),
                            getAmountOfExecutedTests(), maxTestSessions);
                    LOGGER.info(message);
                    cleanupNode(false);
                    // Enabling polling again since the node is still alive.
                    startPolling();
                }
            }
        } catch (Exception e) {
            LOGGER.warn(getId() + " " + e.toString(), e);
        } finally {
            super.afterSession(session);
        }
    }

    @Override
    public void startPolling() {
        super.startPolling();
        String containerId = this.getRegistration().getContainerId();
        
        dockerSeleniumNodePollerThread = new DockerSeleniumNodePoller(this, containerId);
        dockerSeleniumNodePollerThread.start();
    }

    @Override
    public void stopPolling() {
        super.stopPolling();
        dockerSeleniumNodePollerThread.interrupt();
    }

    @Override
    public void teardown() {
        super.teardown();
        stopPolling();
    }

    /*
        Incrementing variable to count the number of tests executed, if possible.
     */
    private synchronized boolean increaseCounter() {
        // Meaning that we have already executed the allowed number of tests.
        if (isTestSessionLimitReached()) {
            return false;
        }
        amountOfExecutedTests++;
        return true;
    }

    /*
        When we shutdown the node because a test finished with a timeout, then we also set the test counter to the
        max amount, otherwise there is a risk where a test is accepted while the node is shutting down.
        This should only be invoked from the shutdownNode() method when the test had been idle.
     */
    private synchronized void stopReceivingTests() {
        amountOfExecutedTests = maxTestSessions;
    }

    private String getCapability(Map<String, Object> requestedCapability, String capabilityName, String defaultValue) {
        return Optional.ofNullable(requestedCapability.get(capabilityName)).orElse(defaultValue).toString();
    }

    @VisibleForTesting
    public TestInformation getTestInformation() {
        return testInformation;
    }

    /*
        Method to decide if the node can be removed based on the amount of executed tests.
     */
    @VisibleForTesting
    public synchronized boolean isTestSessionLimitReached() {
        return getAmountOfExecutedTests() >= maxTestSessions;
    }

    /*
        Method to check for test inactivity, each node only has one slot
     */
    @VisibleForTesting
    protected synchronized boolean isTestIdle() {
        for (TestSlot testSlot : getTestSlots()) {
            if (testSlot.getSession() != null) {
                return testSlot.getSession().getInactivityTime() >= (getMaxTestIdleTimeSecs() * 1000L);
            }
        }
        return false;
    }

    /*
        Method to terminate an idle session via the registry, the code works because each one has only one slot
        We use BROWSER_TIMEOUT as a reason, but this could be changed in the future to show a more clear reason
     */
    @VisibleForTesting
    protected synchronized void terminateIdleTest() {
        for (TestSlot testSlot : getTestSlots()) {
            if (testSlot.getSession() != null) {
                long executionTime = (System.currentTimeMillis() - testSlot.getLastSessionStart()) / 1000;
                ga.testEvent(DockerSeleniumRemoteProxy.class.getName(), testSlot.getSession().getRequestedCapabilities().toString(),
                        executionTime);
                getRegistry().forceRelease(testSlot, SessionTerminationReason.ORPHAN);
            }
        }
    }

    @VisibleForTesting
    protected int getAmountOfExecutedTests() {
        return amountOfExecutedTests;
    }

    @VisibleForTesting
    protected void videoRecording(final DockerSeleniumContainerAction action) {
        if (isVideoRecordingEnabled()) {
            try {
                processContainerAction(action, getContainerId());
            } catch (Exception e) {
                LOGGER.error(getId() + e.toString(), e);
                ga.trackException(e);
            }
        } else {
            String message = String.format("%s %s: Video recording is disabled", getId(), action.getContainerAction());
            LOGGER.info(message);
        }
    }

    public String getTestName() {
        return Optional.ofNullable(testName).orElse("");
    }

    public String getTestBuild() {
        return Optional.ofNullable(testBuild).orElse("");
    }

    public long getMaxTestIdleTimeSecs() {
        if (maxTestIdleTimeSecs > 0) {
            return maxTestIdleTimeSecs;
        }
        return DEFAULT_MAX_TEST_IDLE_TIME_SECS;
    }

    protected String getContainerId() {
        return registration.getContainerId();
    }
    
    public ContainerClientRegistration getRegistration() {
        return registration;
    }

    @VisibleForTesting
    void processContainerAction(final DockerSeleniumContainerAction action, final String containerId) {
        processContainerAction(action, "", containerId);
    }

    @VisibleForTesting
    void processContainerAction(final DockerSeleniumContainerAction action, final String commandParameters,
                                final String containerId) {
        final String[] command = { "bash", "-c", action.getContainerAction().concat(commandParameters)};
        containerClient.executeCommand(containerId, command, action.isWaitForExecution());

        if (keepVideoAndLogs()) {
            if (DockerSeleniumContainerAction.STOP_RECORDING == action) {
                copyVideos(containerId);
            }
            if (DockerSeleniumContainerAction.TRANSFER_LOGS == action) {
                copyLogs(containerId);
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @VisibleForTesting
    void copyVideos(final String containerId) {
        boolean videoWasCopied = false;
        TarArchiveInputStream tarStream = new TarArchiveInputStream(containerClient.copyFiles(containerId, "/videos/"));
        try {
            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String fileExtension = entry.getName().substring(entry.getName().lastIndexOf('.'));
                testInformation.setFileExtension(fileExtension);
                File videoFile = new File(testInformation.getVideoFolderPath(), testInformation.getFileName());
                File parent = videoFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                OutputStream outputStream = new FileOutputStream(videoFile);
                IOUtils.copy(tarStream, outputStream);
                outputStream.close();
                videoWasCopied = true;
                LOGGER.info(String.format("%s Video file copied to: %s/%s", getId(),
                        testInformation.getVideoFolderPath(), testInformation.getFileName()));
            }
        } catch (IOException e) {
            // This error happens in k8s, but the file is ok, nevertheless the size is not accurate
            boolean isPipeClosed = e.getMessage().toLowerCase().contains("pipe closed");
            if (ContainerFactory.getIsKubernetes().get() && isPipeClosed) {
                LOGGER.info(String.format("%s Video file copied to: %s/%s", getId(),
                        testInformation.getVideoFolderPath(), testInformation.getFileName()));
            } else {
                LOGGER.warn(getId() + " Error while copying the video", e);
            }
            ga.trackException(e);
        } finally {
            if (!videoWasCopied) {
                testInformation.setVideoRecorded(false);
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @VisibleForTesting
    void copyLogs(final String containerId) {
        TarArchiveInputStream tarStream = new TarArchiveInputStream(containerClient.copyFiles(containerId, "/var/log/cont/"));
        try {
            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = entry.getName().replace("cont/", "");
                File logFile = new File(testInformation.getLogsFolderPath(), fileName);
                File parent = logFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                OutputStream outputStream = new FileOutputStream(logFile);
                IOUtils.copy(tarStream, outputStream);
                outputStream.close();
            }
            LOGGER.info(String.format("%s Logs copied to: %s", getId(), testInformation.getLogsFolderPath()));
        } catch (IOException e) {
            // This error happens in k8s, but the file is ok, nevertheless the size is not accurate
            boolean isPipeClosed = e.getMessage().toLowerCase().contains("pipe closed");
            if (ContainerFactory.getIsKubernetes().get() && isPipeClosed) {
                LOGGER.info(String.format("%s Logs copied to: %s", getId(), testInformation.getLogsFolderPath()));
            } else {
                LOGGER.warn(getId() + " Error while copying the logs", e);
            }
            ga.trackException(e);
        }
    }

    public boolean isCleaningUpBeforeNextSession() {
        return cleaningUpBeforeNextSession;
    }

    private void setCleaningUpBeforeNextSession(boolean cleaningUpBeforeNextSession) {
        this.cleaningUpBeforeNextSession = cleaningUpBeforeNextSession;
    }

    private void cleanupNode(boolean willShutdown) {
        // This basically means that the node is cleaning up and will receive a new request soon
        // willShutdown == true => there won't be a next session
        this.setCleaningUpBeforeNextSession(!willShutdown);
        try {
            processContainerAction(DockerSeleniumContainerAction.SEND_NOTIFICATION,
                    testInformation.getTestStatus().getTestNotificationMessage(), getContainerId());
            videoRecording(DockerSeleniumContainerAction.STOP_RECORDING);
            processContainerAction(DockerSeleniumContainerAction.TRANSFER_LOGS, getContainerId());
            processContainerAction(DockerSeleniumContainerAction.CLEANUP_CONTAINER, getContainerId());
            if (keepVideoAndLogs()) {
                Dashboard.updateDashboard(testInformation);
            }
        } finally {
            this.setCleaningUpBeforeNextSession(false);
        }
    }

    private boolean keepVideoAndLogs() {
        return !keepOnlyFailedTests || TestInformation.TestStatus.FAILED.equals(testInformation.getTestStatus())
                || TestInformation.TestStatus.TIMEOUT.equals(testInformation.getTestStatus());
    }
    
    private void shutdownNode(boolean isTestIdle) {
        cleanupNode(true);

        String shutdownReason = String.format("%s Marking the node as down because it was stopped after %s tests.",
                getId(), maxTestSessions);

        if (isTestIdle) {
            terminateIdleTest();
            stopReceivingTests();
            shutdownReason = String.format("%s Marking the node as down because the test has been idle for more than %s seconds.",
                    getId(), getMaxTestIdleTimeSecs());
        }

        containerClient.stopContainer(getContainerId());
        addNewEvent(new RemoteUnregisterException(shutdownReason));
    }


    public enum DockerSeleniumContainerAction {
        START_RECORDING("start-video", false),
        STOP_RECORDING("stop-video", true),
        TRANSFER_LOGS("transfer-logs.sh", true),
        CLEANUP_CONTAINER("cleanup-container.sh", true),
        SEND_NOTIFICATION("notify", true),
        CLEAN_NOTIFICATION("killall --ignore-case --quiet --regexp \"xfce4-notifyd.*\"", true);

        private String containerAction;
        private boolean waitForExecution;

        DockerSeleniumContainerAction(String action, boolean waitForExecution) {
            this.containerAction = action;
            this.waitForExecution = waitForExecution;
        }

        public String getContainerAction() {
            return containerAction;
        }

        public boolean isWaitForExecution() {
            return waitForExecution;
        }
    }

    /*
        Class to poll continuously the node status regarding the amount of tests executed. If maxTestSessions
        have been executed, then the node is removed from the grid (this should trigger the docker container to stop).
     */
    static class DockerSeleniumNodePoller extends Thread {

        private static long sleepTimeBetweenChecks = 500;
        private DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy;
        DockerSeleniumNodePoller(DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy, String containerName) {
            super("DockerSeleniumNodePoller container [" + containerName + "]");
            this.dockerSeleniumRemoteProxy = dockerSeleniumRemoteProxy;
        }

        protected long getSleepTimeBetweenChecks() {
            return sleepTimeBetweenChecks;
        }

        @Override
        public void run() {
            while (true) {
                /*
                    If the current session has been idle for a while, the node shuts down
                */
                if (dockerSeleniumRemoteProxy.isTestIdle()) {
                    dockerSeleniumRemoteProxy.testInformation.setTestStatus(TestInformation.TestStatus.TIMEOUT);
                    LOGGER.info(dockerSeleniumRemoteProxy.getId() +
                            " Shutting down node due to test inactivity");
                    dockerSeleniumRemoteProxy.shutdownNode(true);
                    return;
                }
                try {
                    Thread.sleep(getSleepTimeBetweenChecks());
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }


}

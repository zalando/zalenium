package de.zalando.ep.zalenium.proxy;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.zalando.ep.zalenium.streams.InputStreamDescriptor;
import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang3.StringUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.RemoteException;
import org.openqa.grid.common.exception.RemoteNotReachableException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.container.swarm.SwarmUtilities;
import de.zalando.ep.zalenium.dashboard.DashboardCollection;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.matcher.DockerSeleniumCapabilityMatcher;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;

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
    private static final String ZALENIUM_PROXY_CLEANUP_TIMEOUT = "ZALENIUM_PROXY_CLEANUP_TIMEOUT";
    private static final int DEFAULT_PROXY_CLEANUP_TIMEOUT = 180;
    private static final String ZALENIUM_KEEP_ONLY_FAILED_TESTS = "ZALENIUM_KEEP_ONLY_FAILED_TESTS";
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerSeleniumRemoteProxy.class.getName());
    private static final int DEFAULT_MAX_TEST_SESSIONS = 1;
    private static final boolean DEFAULT_KEEP_ONLY_FAILED_TESTS = false;
    private static final Environment defaultEnvironment = new Environment();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(5);
    private static int maxTestSessions;
    private static boolean keepOnlyFailedTests;
    private static boolean videoRecordingEnabledGlobal;
    private static long proxyCleanUpTimeout;
    private static Environment env = defaultEnvironment;
    private final HtmlRenderer renderer = new DefaultProxyHtmlRenderer(this);
    private final ContainerClientRegistration registration;
    private boolean videoRecordingEnabledSession;
    private boolean videoRecordingEnabledConfigured = false;
    private boolean cleaningUp;
    private boolean cleaningUpBeforeNextSession;
    private ContainerClient containerClient = ContainerFactory.getContainerClient();
    private int amountOfExecutedTests;
    private long maxTestIdleTimeSecs;
    private String testBuild;
    private String testName;
    private TestInformation testInformation;
    private GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private CapabilityMatcher capabilityHelper;
    private long lastCommandTime = 0;
    private long cleanupStartedTime = 0;
    private AtomicBoolean timedOut = new AtomicBoolean(false);
    private long timeRegistered = System.currentTimeMillis();
    private boolean stopRecordingByCookie = false;

    public DockerSeleniumRemoteProxy(RegistrationRequest request, GridRegistry registry) {
        super(request, registry);
        try {
            this.amountOfExecutedTests = 0;
            readEnvVars();
            containerClient.setNodeId(getId());
            registration = containerClient.registerNode(DockeredSeleniumStarter.getContainerName(),
                    this.getRemoteHost());
        } catch (Exception e) {
            LOGGER.error("Failed to create", e);
            throw e;
        }
    }

    @VisibleForTesting
    static void readEnvVars() {
        boolean videoEnabled = env.getBooleanEnvVariable(ZALENIUM_VIDEO_RECORDING_ENABLED,
                DEFAULT_VIDEO_RECORDING_ENABLED);
        setVideoRecordingEnabledGlobal(videoEnabled);

        maxTestSessions = env.getIntEnvVariable(ZALENIUM_MAX_TEST_SESSIONS, DEFAULT_MAX_TEST_SESSIONS);
        keepOnlyFailedTests = env.getBooleanEnvVariable(ZALENIUM_KEEP_ONLY_FAILED_TESTS,
                DEFAULT_KEEP_ONLY_FAILED_TESTS);

        long proxyCleanupTO = env.getIntEnvVariable(ZALENIUM_PROXY_CLEANUP_TIMEOUT, DEFAULT_PROXY_CLEANUP_TIMEOUT);
        setProxyCleanUpTimeout(proxyCleanupTO);
    }

    @VisibleForTesting
    protected static void setEnv(final Environment env) {
        DockerSeleniumRemoteProxy.env = env;
    }

    @VisibleForTesting
    static void restoreEnvironment() {
        env = defaultEnvironment;
    }

    public static long getProxyCleanUpTimeout() {
        return proxyCleanUpTimeout;
    }

    public static void setProxyCleanUpTimeout(long proxyCleanUpTimeout) {
        DockerSeleniumRemoteProxy.proxyCleanUpTimeout = proxyCleanUpTimeout < 0 ?
                DEFAULT_PROXY_CLEANUP_TIMEOUT : proxyCleanUpTimeout;
    }

    private static void setVideoRecordingEnabledGlobal(boolean videoRecordingEnabled) {
        DockerSeleniumRemoteProxy.videoRecordingEnabledGlobal = videoRecordingEnabled;
    }

    @Override
    public long getLastSessionStart() {
        return super.getLastSessionStart();
    }

    public long getLastCommandTime() {
        return lastCommandTime;
    }

    @Override
    public void startPolling() {
        // All the health status of containers/pods is controlled by the AutoStartProxySet class.
        LOGGER.debug("startPolling() deactivated");
    }

    @Override
    public void stopPolling() {
        // All the health status of containers/pods is controlled by the AutoStartProxySet class.
        LOGGER.debug("stopPolling() deactivated");
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
    public synchronized TestSession getNewSession(Map<String, Object> requestedCapability) {

        String currentName = configureThreadName();
        LOGGER.debug("Getting new session request {}", requestedCapability);

        if (this.timedOut.get()) {
            LOGGER.debug("Proxy has timed out, not accepting new sessions.");
            setThreadName(currentName);
            return null;
        }

        /*
            Validate first if the capability is matched
         */
        if (!hasCapability(requestedCapability)) {
            return null;
        }

        if (!requestedCapability.containsKey(CapabilityType.BROWSER_NAME)) {
            LOGGER.debug("Capability {} does not contain {} key, a browser test cannot start without it.",
                    requestedCapability, CapabilityType.BROWSER_NAME);
            setThreadName(currentName);
            return null;
        }

        if (!this.isBusy() && increaseCounter()) {
            setThreadName(currentName);
            return createNewSession(requestedCapability);
        }

        LOGGER.debug("No more sessions allowed");
        setThreadName(currentName);
        return null;
    }

    private TestSession createNewSession(Map<String, Object> requestedCapability) {
        String currentName = configureThreadName();
        TestSession newSession = super.getNewSession(requestedCapability);
        if (newSession == null) {
            // The node has been marked down.
            LOGGER.debug(" Proxy was marked down after being assigned, returning null");
            setThreadName(currentName);
            return null;
        }

        LOGGER.debug("Creating session for {}", requestedCapability);

        maxTestIdleTimeSecs = getConfiguredIdleTimeout(requestedCapability);

        lastCommandTime = System.currentTimeMillis();

        setThreadName(currentName);
        ensureTestInformation(newSession);
        return newSession;
    }

    @Override
    public CapabilityMatcher getCapabilityHelper() {
        if (capabilityHelper == null) {
            capabilityHelper = new DockerSeleniumCapabilityMatcher();
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
            LOGGER.warn(e.toString());
            LOGGER.debug(e.toString(), e);
        }
        if (configuredIdleTimeout <= 0) {
            configuredIdleTimeout = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
        }
        return configuredIdleTimeout;
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        String currentName = configureThreadName();
        ensureTestInformation(session);
        super.beforeCommand(session, request, response);
        LOGGER.debug("lastCommand: {} - executing...", request.getMethod(), request.getPathInfo());
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            try {
                if (seleniumRequest.getPathInfo().endsWith("cookie")) {
                    LOGGER.debug("Checking for cookies... {}", seleniumRequest.getBody());
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
                        if (ContainerFactory.getIsKubernetes().get()) {
                            // https://github.com/zalando/zalenium/issues/763
                            message = message.replace("#","");
                        }
                        String messageCommand = String.format(" 'Zalenium', '%s', --icon=/home/seluser/images/message.png",
                                message);
                        processContainerAction(DockerSeleniumContainerAction.CLEAN_NOTIFICATION, getContainerId());
                        processContainerAction(DockerSeleniumContainerAction.SEND_NOTIFICATION, messageCommand,
                                getContainerId());
                    }
                    if ("zaleniumVideo".equalsIgnoreCase(cookieName)) {
                        boolean recordVideo = Boolean.parseBoolean(cookie.get("value").getAsString());
                        if (recordVideo && !isVideoRecordingEnabled()) {
                            setVideoRecordingEnabledSession(true);
                            testInformation.setVideoRecorded(true);
                            stopRecordingByCookie = false;
                            videoRecording(DockerSeleniumContainerAction.START_RECORDING);
                        } else if (!recordVideo && isVideoRecordingEnabled()){
                            stopRecordingByCookie = true;
                            videoRecording(DockerSeleniumContainerAction.STOP_RECORDING);
                            setVideoRecordingEnabledSession(false);
                            testInformation.setVideoRecorded(false);
                        }
                    }
                    if ("zaleniumTestName".equalsIgnoreCase(cookieName)) {
                        String newTestName = cookie.get("value").getAsString();
                        if (!newTestName.isEmpty()) {
                            testName = newTestName;
                            testInformation.setTestName(testName);
                        }
                    }
                    else if(CommonProxyUtilities.metadataCookieName.equalsIgnoreCase(cookieName)) {
                        JsonParser jsonParser = new JsonParser();
                        JsonObject metadata = jsonParser.parse(cookie.get("value").getAsString()).getAsJsonObject();
                        testInformation.setMetadata(metadata);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("There was an error while checking for cookies.", e);
            }
        }
        setThreadName(currentName);
    }

    @Override
    public void afterCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        String currentName = configureThreadName();
        super.afterCommand(session, request, response);
        LOGGER.debug("lastCommand: {} - executing...", request.getMethod(), request.getPathInfo());
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (RequestType.START_SESSION.equals(seleniumRequest.getRequestType())) {
                ExternalSessionKey externalKey = Optional.ofNullable(session.getExternalKey())
                        .orElse(new ExternalSessionKey("[No external key present]"));
                LOGGER.debug(String.format("Test session started with internal key %s and external key %s assigned to remote %s.",
                        session.getInternalKey(),
                        externalKey,
                        getId()));
                LOGGER.debug("Test session started with internal key {} and external key {} assigned to remote.",
                        session.getInternalKey(), externalKey);
                videoRecording(DockerSeleniumContainerAction.START_RECORDING);
            }
        }
        this.lastCommandTime = System.currentTimeMillis();
        setThreadName(currentName);
    }

    private void ensureTestInformation(TestSession session) {
        String seleniumSessionId = session.getExternalKey() != null ?
                session.getExternalKey().getKey() :
                session.getInternalKey();

        if(testInformation != null && testInformation.getSeleniumSessionId().equals(seleniumSessionId)) {
            return;
        }
        Map<String, Object> requestedCapability = session.getRequestedCapabilities();

        LOGGER.info("External ssid {} internal {} ", session.getExternalKey(), session.getInternalKey());


        String browserName = requestedCapability.get(CapabilityType.BROWSER_NAME).toString();
        testName = getCapability(requestedCapability, ZaleniumCapabilityType.TEST_NAME, "");

        if (testName.isEmpty()) {
            testName = seleniumSessionId;
        }
        testBuild = getCapability(requestedCapability, ZaleniumCapabilityType.BUILD_NAME, "");
        if (requestedCapability.containsKey(ZaleniumCapabilityType.RECORD_VIDEO)) {
            boolean videoRecording = Boolean.parseBoolean(getCapability(requestedCapability, ZaleniumCapabilityType.RECORD_VIDEO, "true"));
            setVideoRecordingEnabledSession(videoRecording);
        }
        String testFileNameTemplate = getCapability(requestedCapability, ZaleniumCapabilityType.TEST_FILE_NAME_TEMPLATE, "");
        String screenResolution = getCapability(session.getSlot().getCapabilities(), ZaleniumCapabilityType.SCREEN_RESOLUTION, "N/A");
        String browserVersion = getCapability(session.getSlot().getCapabilities(), CapabilityType.VERSION, "");
        String timeZone = getCapability(session.getSlot().getCapabilities(), ZaleniumCapabilityType.TIME_ZONE, "N/A");
        testInformation = new TestInformation.TestInformationBuilder()
                .withTestName(testName)
                .withSeleniumSessionId(seleniumSessionId)
                .withProxyName("Zalenium")
                .withBrowser(browserName)
                .withBrowserVersion(browserVersion)
                .withPlatform(Platform.LINUX.name())
                .withScreenDimension(screenResolution)
                .withTimeZone(timeZone)
                .withTestFileNameTemplate(testFileNameTemplate)
                .withBuild(testBuild)
                .withTestStatus(TestInformation.TestStatus.COMPLETED)
                .build();
        testInformation.setVideoRecorded(isVideoRecordingEnabled());

        super.beforeSession(session);
    }

    @Override
    public void afterSession(TestSession session) {
        String currentName = configureThreadName();
        try {
            // This means that the shutdown command was triggered before receiving this afterSession command
            if (!TestInformation.TestStatus.TIMEOUT.equals(testInformation.getTestStatus())) {
                long executionTime = (System.currentTimeMillis() - session.getSlot().getLastSessionStart()) / 1000;
                ga.testEvent(DockerSeleniumRemoteProxy.class.getName(), session.getRequestedCapabilities().toString(),
                        executionTime);
                if (isTestSessionLimitReached()) {
                    LOGGER.info("Session {} completed. Node should shutdown soon...", session.getInternalKey());
                    cleanupNode(true);
                }
                else {
                    LOGGER.info("Session {} completed. Cleaning up node for reuse, used {} of max {} sessions",
                            session.getInternalKey(), getAmountOfExecutedTests(), maxTestSessions);
                    cleanupNode(false);
                }
            }
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
        } finally {
            super.afterSession(session);
        }
        setThreadName(currentName);
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

    public boolean shutdownIfIdle() {
        String currentName = configureThreadName();
        boolean testIdle = isTestIdle();
        boolean testSessionLimitReached = isTestSessionLimitReached();
        boolean isShutdownIfIdle = testIdle || (testSessionLimitReached && !isBusy());
        if (isShutdownIfIdle) {
            LOGGER.debug("Proxy is idle.");
            timeout("proxy being idle after test.", (testSessionLimitReached ?
                    ShutdownType.MAX_TEST_SESSIONS_REACHED : ShutdownType.IDLE));
        }
        setThreadName(currentName);
        return isShutdownIfIdle;
    }

    public boolean shutdownIfStale() {
        String currentName = configureThreadName();
        if (isBusy() && isTestIdle() && !isCleaningUp()) {
            LOGGER.debug("No test activity been recorded recently, proxy is stale.");
            timeout("proxy being stuck | stale during a test.", ShutdownType.STALE);
            setThreadName(currentName);
            return true;
        } else if (isTestSessionLimitReached() && !isBusy()) {
            LOGGER.debug("Proxy has reached max test sessions.");
            timeout("proxy has reached max test sessions.", ShutdownType.MAX_TEST_SESSIONS_REACHED);
            setThreadName(currentName);
            return true;
        }
        setThreadName(currentName);
        return false;
    }

    /*
        Method to check for test inactivity, each node only has one slot
     */
    @VisibleForTesting
    public synchronized boolean isTestIdle() {

        if (this.timedOut.get()) {
            return true;
        } else {
            long timeLastUsed = Math.max(timeRegistered, lastCommandTime);

            long timeSinceUsed = System.currentTimeMillis() - timeLastUsed;

            if (timeSinceUsed > (getMaxTestIdleTimeSecs() * 1000L)) {
                LOGGER.debug("No test activity, proxy has has been idle {} which is more than {}", timeSinceUsed,
                        getMaxTestIdleTimeSecs() * 1000L);
                return true;
            } else {
                return false;
            }
        }
    }

    public synchronized void markDown() {
        if (!this.timedOut.getAndSet(true)) {
            String currentName = configureThreadName();
            LOGGER.info("Marking node down.");
            setThreadName(currentName);
        }
    }

    private void timeout(String reason, ShutdownType shutdownType) {
        boolean shutDown = false;

        synchronized (this) {
            if (this.testInformation != null && this.testInformation.getTestStatus() == null) {
                this.testInformation.setTestStatus(TestInformation.TestStatus.TIMEOUT);
            }

            if (!this.timedOut.getAndSet(true)) {
                LOGGER.debug("Shutting down node due to {}", reason);
                shutDown = true;
            }
        }

        if (shutDown) {
            EXECUTOR_SERVICE.execute(() -> shutdownNode(shutdownType));
        }
    }

    public boolean isTimedOut() {
        return this.timedOut.get();
    }

    /*
        Method to terminate an idle session via the registry, the code works because each one has only one slot
        We use BROWSER_TIMEOUT as a reason, but this could be changed in the future to show a more clear reason
     */
    @VisibleForTesting
    protected void terminateIdleTest() {
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
        String currentName = configureThreadName();
        if (isVideoRecordingEnabled()) {
            try {
                processContainerAction(action, getContainerId());
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
                ga.trackException(e);
            }
        } else {
            LOGGER.debug("{}: Video recording is disabled", action.getContainerAction());
        }
        setThreadName(currentName);
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
        if (DockerSeleniumContainerAction.TRANSFER_LOGS != action) {
            final String[] command = { "bash", "-c", action.getContainerAction().concat(commandParameters)};
            containerClient.executeCommand(containerId, command, action.isWaitForExecution());
        }

        if (keepVideoAndLogs()) {
            if (DockerSeleniumContainerAction.STOP_RECORDING == action) {
                copyVideos(containerId);
                if (stopRecordingByCookie) {
                    DashboardCollection.updateDashboard(testInformation);
                }
            }
            if (DockerSeleniumContainerAction.TRANSFER_LOGS == action) {
                copyLogs(containerId);
            }
        }
    }

    @VisibleForTesting
    void copyVideos(final String containerId) {
        if (testInformation == null || StringUtils.isEmpty(containerId)) {
            // No tests run or container has been removed, nothing to copy and nothing to update.
            return;
        }
        String currentName = configureThreadName();
        boolean videoWasCopied = false;
        InputStreamGroupIterator tarStream = containerClient.copyFiles(containerId, "/videos/");
        try {
            InputStreamDescriptor entry;
            while ((entry = tarStream.next()) != null) {
                String fileExtension = entry.name().substring(entry.name().lastIndexOf('.'));
                testInformation.setFileExtension(fileExtension);
                Path videoFile = Paths.get(String.format("%s/%s", testInformation.getVideoFolderPath(),
                        testInformation.getFileName()));
                if (!Files.exists(videoFile.getParent())) {
                    Files.createDirectories(videoFile.getParent());
                }

                //For multiple recording in one session, add '_{count}' to the test name
                if (Files.exists(videoFile)) {
                    testInformation.setTestName(testName + "_" + testInformation.getFileCount());
                    testInformation.buildVideoFileName();
                    videoFile = Paths.get(String.format("%s/%s", testInformation.getVideoFolderPath(),
                            testInformation.getFileName()));
                }
                Files.copy(entry.get(), videoFile, StandardCopyOption.REPLACE_EXISTING);
                CommonProxyUtilities.setFilePermissions(videoFile);
                videoWasCopied = true;
                testInformation.setFileCount(testInformation.getFileCount() + 1);
                LOGGER.debug("Video file copied to: {}/{}", testInformation.getVideoFolderPath(), testInformation.getFileName());
            }
        } catch (IOException e) {
            // This error happens in k8s, but the file is ok, nevertheless the size is not accurate
            boolean isPipeClosed = e.getMessage().toLowerCase().contains("pipe closed");
            if (ContainerFactory.getIsKubernetes().get() && isPipeClosed) {
                LOGGER.debug("Video file copied to: {}/{}", testInformation.getVideoFolderPath(), testInformation.getFileName());
            } else {
                LOGGER.warn("Error while copying the video", e);
            }
            ga.trackException(e);
        } finally {
            if (!videoWasCopied) {
                testInformation.setVideoRecorded(false);
            }
        }
        setThreadName(currentName);
    }

    @VisibleForTesting
    void copyLogs(final String containerId) {
        if (SwarmUtilities.isSwarmActive()) {
            // Disabling logs in swarm mode
            return;
        }

        if (testInformation == null || StringUtils.isEmpty(containerId)) {
            // No tests run or container has been removed, nothing to copy and nothing to update.
            return;
        }
        String currentName = configureThreadName();
        InputStreamGroupIterator tarStream = containerClient.copyFiles(containerId, "/var/log/cont/");
        try {
            InputStreamDescriptor entry;
            while ((entry = tarStream.next()) != null) {
                if (!Files.exists(Paths.get(testInformation.getLogsFolderPath()))) {
                    Path directories = Files.createDirectories(Paths.get(testInformation.getLogsFolderPath()));
                    CommonProxyUtilities.setFilePermissions(directories);
                    CommonProxyUtilities.setFilePermissions(directories.getParent());
                }
                String fileName = entry.name().replace("cont/", "");
                Path logFile = Paths.get(String.format("%s/%s", testInformation.getLogsFolderPath(), fileName));
                Files.copy(entry.get(), logFile);
                CommonProxyUtilities.setFilePermissions(logFile);
            }
            LOGGER.debug("Logs copied to: {}", testInformation.getLogsFolderPath());
        } catch (IOException | NullPointerException e) {
            // This error happens in k8s, but the file is ok, nevertheless the size is not accurate
            String exceptionMessage = Optional.ofNullable(e.getMessage()).orElse("");
            boolean isPipeClosed = exceptionMessage.toLowerCase().contains("pipe closed");
            if (ContainerFactory.getIsKubernetes().get() && isPipeClosed) {
                LOGGER.debug("Logs copied to: {}", testInformation.getLogsFolderPath());
            } else {
                LOGGER.debug("Error while copying the logs", e);
            }
            ga.trackException(e);
        }
        setThreadName(currentName);
    }

    private boolean isCleaningUp() {
        // A node should not be marked as stale while doing cleanup jobs. SANITY: The upper limit of cleanup jobs is 3 minutes.
        long timeSinceCleanupStarted = System.currentTimeMillis() - cleanupStartedTime;

        if(this.cleaningUp && timeSinceCleanupStarted > (getProxyCleanUpTimeout() * 1000L)) {
            LOGGER.error("Proxy has been cleaning up {} which is longer than {}. The Grid seems to be overloaded. " +
                            "You can extend this timeout through the ZALENIUM_PROXY_CLEANUP_TIMEOUT env var.",
                    timeSinceCleanupStarted, (getProxyCleanUpTimeout() * 1000));
            //Cleanup is taking more then getProxyCleanUpTimeout() minutes, return false so that the node can get
            // marked as stale.
            return false;
        } else {
            return this.cleaningUp;
        }
    }

    private void setCleaningUp(boolean cleaningUp) {
        this.cleaningUp = cleaningUp;
    }

    public boolean isCleaningUpBeforeNextSession() {
        return cleaningUpBeforeNextSession;
    }

    private void setCleaningUpBeforeNextSession(boolean cleaningUpBeforeNextSession) {
        this.cleaningUpBeforeNextSession = cleaningUpBeforeNextSession;
    }

    private void setCleaningMarker(boolean willShutdown) {
        this.cleanupStartedTime = System.currentTimeMillis();
        this.setCleaningUp(true);
        this.setCleaningUpBeforeNextSession(willShutdown);
    }

    private void unsetCleaningMarker() {
        this.setCleaningUp(false);
        this.setCleaningUpBeforeNextSession(false);
    }

    private void cleanupNode(boolean willShutdown) {
        // This basically means that the node is cleaning up and will receive a new request soon
        // willShutdown == true => there won't be a next session
        if (!isCleaningUp()) {
            this.setCleaningMarker(!willShutdown);

            try {
                if (testInformation != null) {
                    processContainerAction(DockerSeleniumContainerAction.SEND_NOTIFICATION,
                        testInformation.getTestStatus().getTestNotificationMessage(), getContainerId());
                }
                videoRecording(DockerSeleniumContainerAction.STOP_RECORDING);
                processContainerAction(DockerSeleniumContainerAction.TRANSFER_LOGS, getContainerId());
                processContainerAction(DockerSeleniumContainerAction.CLEANUP_CONTAINER,
                    getContainerId());

                if (testInformation != null && keepVideoAndLogs()) {
                    DashboardCollection.updateDashboard(testInformation);
                }
            } finally {
                this.unsetCleaningMarker();
            }
        }
    }

    private boolean keepVideoAndLogs() {
        return !keepOnlyFailedTests || TestInformation.TestStatus.FAILED.equals(testInformation.getTestStatus())
                || TestInformation.TestStatus.TIMEOUT.equals(testInformation.getTestStatus());
    }

    public void shutdownNode(ShutdownType shutdownType) {
        String currentName = configureThreadName();
        String shutdownReason;
        if (shutdownType == ShutdownType.MAX_TEST_SESSIONS_REACHED) {
            shutdownReason = String.format(
                    "Marking the node as down because it was stopped after %s tests.", maxTestSessions);
        }
        else {
            shutdownReason = "Marking the node as down because it was idle after the tests had finished.";
        }

        if (shutdownType == ShutdownType.STALE) {
            cleanupNode(true);
            terminateIdleTest();
            stopReceivingTests();
            shutdownReason = String.format(
                    "Marking the node as down because the test has been idle for more than %s seconds.",
                    getMaxTestIdleTimeSecs());
        }

        containerClient.stopContainer(getContainerId());

        addNewEvent(new RemoteUnregisterException(shutdownReason));
        setThreadName(currentName);
    }

    @Override
    public void onEvent(List<RemoteException> events, RemoteException lastInserted) {
        List<RemoteException> remoteNotReachableEvents = events.stream()
                .filter(event -> event instanceof RemoteNotReachableException)
                .collect(Collectors.toList());
        super.onEvent(remoteNotReachableEvents, lastInserted);

        if (lastInserted instanceof RemoteUnregisterException) {
            LOGGER.debug(lastInserted.getMessage());
            GridRegistry registry = this.getRegistry();
            registry.removeIfPresent(this);
        }
    }

    private String configureThreadName() {
        String currentName = Thread.currentThread().getName();
        setThreadName(getId());
        return currentName;
    }

    private void setThreadName(String name) {
        Thread.currentThread().setName(name);
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

    public enum ShutdownType {
        STALE,
        IDLE,
        MAX_TEST_SESSIONS_REACHED
    }


}

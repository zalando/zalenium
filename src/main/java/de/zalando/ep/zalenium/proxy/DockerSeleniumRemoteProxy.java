package de.zalando.ep.zalenium.proxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerFactory;
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

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(5);

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

    @Override
    public long getLastSessionStart() {
        return super.getLastSessionStart();
    }

    public long getLastCommandTime() {
        return lastCommandTime;
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
    public synchronized TestSession getNewSession(Map<String, Object> requestedCapability) {

        LOGGER.debug(String.format("%s getting new session %s", getContainerId(), this.timedOut.get()));

        if (this.timedOut.get()) {
            LOGGER.debug(String.format("%s has timed out - not accepting session.", getContainerId()));
            return null;
        } else {
            LOGGER.debug(String.format("%s has not timed out.", getContainerId()));
        }

        /*
            Validate first if the capability is matched
         */
        if (!hasCapability(requestedCapability)) {
            return null;
        }

        if (!requestedCapability.containsKey(CapabilityType.BROWSER_NAME)) {
            LOGGER.debug(String.format("%s Capability %s does not contain %s key, a browser test cannot " +
                            "start without it.", getContainerId(), requestedCapability, CapabilityType.BROWSER_NAME));
            return null;
        }

        if (!this.isBusy() && increaseCounter()) {
            return createNewSession(requestedCapability);
        }
        LOGGER.debug("{} No more sessions allowed", getContainerId());
        return null;
    }

    private TestSession createNewSession(Map<String, Object> requestedCapability) {
        TestSession newSession = super.getNewSession(requestedCapability);
        LOGGER.debug(getContainerId() + " Creating session for: " + requestedCapability.toString());
        if (newSession == null) {
            // The node has been marked down.
            LOGGER.debug(getContainerId() + " was marked down after being assigned, returning null");
            return null;
        }
        
        LOGGER.debug(getContainerId() + " Creating session for: " + requestedCapability.toString());
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
        String testFileNameTemplate = getCapability(requestedCapability, ZaleniumCapabilityType.TEST_FILE_NAME_TEMPLATE, "");
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
                .withTestFileNameTemplate(testFileNameTemplate)
                .withBuild(testBuild)
                .withTestStatus(TestInformation.TestStatus.COMPLETED)
                .build();
        testInformation.setVideoRecorded(isVideoRecordingEnabled());
        maxTestIdleTimeSecs = getConfiguredIdleTimeout(requestedCapability);
        
        lastCommandTime = System.currentTimeMillis();
        
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
            LOGGER.warn(getContainerId() + " " + e.toString());
            LOGGER.debug(getContainerId() + " " + e.toString(), e);
        }
        if (configuredIdleTimeout <= 0) {
            configuredIdleTimeout = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
        }
        return configuredIdleTimeout;
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        Thread.currentThread().setName(getId());
        super.beforeCommand(session, request, response);
        LOGGER.debug(getContainerId() + " lastCommand: " +  request.getMethod() + " - " + request.getPathInfo() + " executing...");
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            try {
                if (seleniumRequest.getPathInfo().endsWith("cookie")) {
                    LOGGER.debug(getContainerId() + " Checking for cookies... " + seleniumRequest.getBody());
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
    }

    @Override
    public void afterCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        Thread.currentThread().setName(getId());
        super.afterCommand(session, request, response);
        LOGGER.debug(getContainerId() + " lastCommand: " +  request.getMethod() + " - " + request.getPathInfo() + " executed.");
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (RequestType.START_SESSION.equals(seleniumRequest.getRequestType())) {
                
                ExternalSessionKey externalKey = Optional.ofNullable(session.getExternalKey())
                    .orElse(new ExternalSessionKey("[No external key present]"));
                LOGGER.debug(String.format("Test session started with internal key %s and external key %s assigned to remote %s.",
                              session.getInternalKey(),
                              externalKey,
                              getId()));
                videoRecording(DockerSeleniumContainerAction.START_RECORDING);
            }
        }
        
        this.lastCommandTime = System.currentTimeMillis();
    }

    @Override
    public void afterSession(TestSession session) {
        try {
            Thread.currentThread().setName(getId());
            // This means that the shutdown command was triggered before receiving this afterSession command
            if (!TestInformation.TestStatus.TIMEOUT.equals(testInformation.getTestStatus())) {
                long executionTime = (System.currentTimeMillis() - session.getSlot().getLastSessionStart()) / 1000;
                ga.testEvent(DockerSeleniumRemoteProxy.class.getName(), session.getRequestedCapabilities().toString(),
                        executionTime);
                // This avoids shutting down the node by timeout in case the file copying takes too long.
                stopPolling();
                if (isTestSessionLimitReached()) {
                    LOGGER.info(String.format("Session %s completed. Node should shutdown soon...", session.getInternalKey()));
                    cleanupNode(true);
                }
                else {
                    String message = String.format(
                            "Session %s completed. Cleaning up node for reuse, used %s of max %s sessions",
                            session.getInternalKey(), getAmountOfExecutedTests(), maxTestSessions);
                    LOGGER.info(message);
                    cleanupNode(false);
                    // Enabling polling again since the node is still alive.
                    startPolling();
                }
            }
        } catch (Exception e) {
            LOGGER.warn(getContainerId() + " " + e.toString(), e);
        } finally {
            super.afterSession(session);
        }
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
        boolean testIdle = isTestIdle();
        boolean testSessionLimitReached = isTestSessionLimitReached();
        if (testIdle || (testSessionLimitReached && !isBusy())) {
            LOGGER.info(String.format("[%s] is idle.", getContainerId()));
            timeout("proxy being idle after test.", (testSessionLimitReached? ShutdownType.MAX_TEST_SESSIONS_REACHED : ShutdownType.IDLE));
            return true;
        } else {
            return false;
        }
    }

    public boolean shutdownIfStale() {
        if (isBusy() && isTestIdle() && !isCleaningUp()) {
            LOGGER.info(String.format("[%s] is stale.", getContainerId()));
            timeout("proxy being stuck | stale during a test.", ShutdownType.STALE);
            return true;
        } else if (isTestSessionLimitReached() && !isBusy()) {
            LOGGER.info(String.format("[%s] has reached max test sessions.", getContainerId()));
            timeout("proxy has reached max test sessions.", ShutdownType.MAX_TEST_SESSIONS_REACHED);
            return true;
        } else {
            return false;
        }
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
                LOGGER.info(String.format("[%s] has been idle [%d] which is more than [%d]", this.getContainerId(),
                        timeSinceUsed, (getMaxTestIdleTimeSecs() * 1000L)));
                return true;
            } else {
                return false;
            }
        }
    }
    
    public synchronized void markDown() {
        if (!this.timedOut.getAndSet(true)) {
            LOGGER.info(getContainerId() + " Marking node down.");
        }
    }

    private void timeout(String reason, ShutdownType shutdownType) {
        boolean shutDown = false;

        synchronized (this) {
            if (this.testInformation != null && this.testInformation.getTestStatus() == null) {
                this.testInformation.setTestStatus(TestInformation.TestStatus.TIMEOUT);
            }

            if (!this.timedOut.getAndSet(true)) {
                LOGGER.info(getContainerId() + " Shutting down node due to " + reason);
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
        if (isVideoRecordingEnabled()) {
            try {
                processContainerAction(action, getContainerId());
            } catch (Exception e) {
                LOGGER.error(getContainerId() + e.toString(), e);
                ga.trackException(e);
            }
        } else {
            String message = String.format("%s %s: Video recording is disabled", getContainerId(), action.getContainerAction());
            LOGGER.debug(message);
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
        if (testInformation == null) {
            // No tests run, nothing to copy and nothing to update.
            return;
        }

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
                LOGGER.debug("{} Video file copied to: {}/{}", getContainerId(),
                    testInformation.getVideoFolderPath(), testInformation.getFileName());
            }
        } catch (IOException e) {
            // This error happens in k8s, but the file is ok, nevertheless the size is not accurate
            boolean isPipeClosed = e.getMessage().toLowerCase().contains("pipe closed");
            if (ContainerFactory.getIsKubernetes().get() && isPipeClosed) {
                LOGGER.debug("{} Video file copied to: {}/{}", getContainerId(),
                    testInformation.getVideoFolderPath(), testInformation.getFileName());
            } else {
                LOGGER.warn(getContainerId() + " Error while copying the video", e);
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
        if (testInformation == null) {
            // No tests run, nothing to copy and nothing to update.
            return;
        }

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
            LOGGER.debug("{} Logs copied to: {}", new Object[]{getContainerId(), testInformation.getLogsFolderPath()});
        } catch (IOException | NullPointerException e) {
            // This error happens in k8s, but the file is ok, nevertheless the size is not accurate
            String exceptionMessage = Optional.ofNullable(e.getMessage()).orElse("");
            boolean isPipeClosed = exceptionMessage.toLowerCase().contains("pipe closed");
            if (ContainerFactory.getIsKubernetes().get() && isPipeClosed) {
                LOGGER.debug("{} Logs copied to: {}", new Object[]{getContainerId(), testInformation.getLogsFolderPath()});
            } else {
                LOGGER.debug(getContainerId() + " Error while copying the logs", e);
            }
            ga.trackException(e);
        }
    }
    
    private boolean isCleaningUp() {
        // A node should not be marked as stale while doing cleanup jobs. SANITY: The upper limit of cleanup jobs is 3 minutes.
        long timeSinceCleanupStarted = System.currentTimeMillis() - cleanupStartedTime;
        
        if(this.cleaningUp && timeSinceCleanupStarted > (180L * 1000L)) {
            LOGGER.error(String.format("[%s] has been cleaning up [%d] which is more than [%d]. The grid seems to be overloaded.", this.getContainerId(),
                    timeSinceCleanupStarted, (180L * 1000L)));
                
            //Cleanup is taking more then 3 minutes, return false so that the node can get marked as stale.
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
        this.setCleaningMarker(!willShutdown);

        try {
            if (testInformation != null) {
                processContainerAction(DockerSeleniumContainerAction.SEND_NOTIFICATION,
                        testInformation.getTestStatus().getTestNotificationMessage(), getContainerId());
            }
            videoRecording(DockerSeleniumContainerAction.STOP_RECORDING);
            processContainerAction(DockerSeleniumContainerAction.TRANSFER_LOGS, getContainerId());
            processContainerAction(DockerSeleniumContainerAction.CLEANUP_CONTAINER, getContainerId());

            if (testInformation != null && keepVideoAndLogs()) {
                DashboardCollection.updateDashboard(testInformation);
            }
        } finally {
            this.unsetCleaningMarker();
        }
    }

    private boolean keepVideoAndLogs() {
        return !keepOnlyFailedTests || TestInformation.TestStatus.FAILED.equals(testInformation.getTestStatus())
                || TestInformation.TestStatus.TIMEOUT.equals(testInformation.getTestStatus());
    }
    
    public void shutdownNode(ShutdownType shutdownType) {
        String shutdownReason;
        if (shutdownType == ShutdownType.MAX_TEST_SESSIONS_REACHED) {
            shutdownReason = String.format(
                    "%s Marking the node as down because it was stopped after %s tests.", getContainerId(),
                    maxTestSessions);
        }
        else {
            shutdownReason = String.format(
                    "%s Marking the node as down because it was idle after the tests had finished.", getContainerId());
        }

        if (shutdownType == ShutdownType.STALE) {
            cleanupNode(true);
            terminateIdleTest();
            stopReceivingTests();
            shutdownReason = String.format(
                    "%s Marking the node as down because the test has been idle for more than %s seconds.",
                    getContainerId(), getMaxTestIdleTimeSecs());
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

}
enum ShutdownType {
    STALE,
    IDLE,
    MAX_TEST_SESSIONS_REACHED
}

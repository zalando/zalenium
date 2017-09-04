package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.matcher.DockerSeleniumCapabilityMatcher;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.RemoteNotReachableException;
import org.openqa.grid.common.exception.RemoteUnregisterException;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.SessionTerminationReason;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.beta.WebProxyHtmlRendererBeta;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    The implementation of this class was inspired on https://gist.github.com/krmahadevan/4649607
 */
@SuppressWarnings("WeakerAccess")
public class DockerSeleniumRemoteProxy extends DefaultRemoteProxy {

    @VisibleForTesting
    static final String ZALENIUM_VIDEO_RECORDING_ENABLED = "ZALENIUM_VIDEO_RECORDING_ENABLED";
    @VisibleForTesting
    static final boolean DEFAULT_VIDEO_RECORDING_ENABLED = true;
    @VisibleForTesting
    static final long DEFAULT_MAX_TEST_IDLE_TIME_SECS = 90L;
    private static final Logger LOGGER = Logger.getLogger(DockerSeleniumRemoteProxy.class.getName());
    private static final int MAX_UNIQUE_TEST_SESSIONS = 1;
    private static final Environment defaultEnvironment = new Environment();
    private static boolean videoRecordingEnabledGlobal;
    private static Environment env = defaultEnvironment;
    private final HtmlRenderer renderer = new WebProxyHtmlRendererBeta(this);
    private final ContainerClientRegistration registration;
    private boolean videoRecordingEnabledSession;
    private boolean videoRecordingEnabledConfigured = false;
    private ContainerClient containerClient = ContainerFactory.getContainerClient();
    private int amountOfExecutedTests;
    private long maxTestIdleTimeSecs;
    private String testGroup;
    private String testName;
    private TestInformation testInformation;
    private DockerSeleniumNodePoller dockerSeleniumNodePollerThread = null;
    private GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private CapabilityMatcher capabilityHelper;

    public DockerSeleniumRemoteProxy(RegistrationRequest request, Registry registry) {
        super(request, registry);
        this.amountOfExecutedTests = 0;
        readEnvVarForVideoRecording();
        containerClient.setNodeId(getId());
        registration = containerClient.registerNode(DockerSeleniumStarterRemoteProxy.getContainerName(), this.getRemoteHost());
    }

    @VisibleForTesting
    static void readEnvVarForVideoRecording() {
        boolean videoEnabled = env.getBooleanEnvVariable(ZALENIUM_VIDEO_RECORDING_ENABLED,
                DEFAULT_VIDEO_RECORDING_ENABLED);
        setVideoRecordingEnabledGlobal(videoEnabled);
    }

    @VisibleForTesting
    protected static void setEnv(final Environment env) {
        DockerSeleniumRemoteProxy.env = env;
    }

    @VisibleForTesting
    static void restoreEnvironment() {
        env = defaultEnvironment;
    }

    @VisibleForTesting
    protected boolean isVideoRecordingEnabled() {
        if (this.videoRecordingEnabledConfigured) {
            return this.videoRecordingEnabledSession;
        }
        return DockerSeleniumRemoteProxy.videoRecordingEnabledGlobal;
    }

    private static void setVideoRecordingEnabledGlobal(boolean videoRecordingEnabled) {
        DockerSeleniumRemoteProxy.videoRecordingEnabledGlobal = videoRecordingEnabled;
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
        if (increaseCounter()) {
            TestSession newSession = super.getNewSession(requestedCapability);
            LOGGER.log(Level.FINE, getId() + " Creating session for: " + requestedCapability.toString());
            String browserName = requestedCapability.get(CapabilityType.BROWSER_NAME).toString();
            testName = getCapability(requestedCapability, "name", "");
            if (testName.isEmpty()) {
                testName = newSession.getExternalKey() != null ?
                        newSession.getExternalKey().getKey() :
                        newSession.getInternalKey();
            }
            testGroup = getCapability(requestedCapability, "group", "");
            if (requestedCapability.containsKey("recordVideo")) {
                boolean videoRecording = Boolean.parseBoolean(getCapability(requestedCapability, "recordVideo", "true"));
                setVideoRecordingEnabledSession(videoRecording);
            }
            String browserVersion = getCapability(newSession.getSlot().getCapabilities(), "version", "");
            testInformation = new TestInformation(testName, testName, "Zalenium", browserName, browserVersion,
                    Platform.LINUX.name());
            testInformation.setVideoRecorded(isVideoRecordingEnabled());
            maxTestIdleTimeSecs = getConfiguredIdleTimeout(requestedCapability);
            return newSession;
        }
        LOGGER.log(Level.FINE, "{0} No more sessions allowed", getId());
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
            Object idleTimeout = requestedCapability.getOrDefault("idleTimeout", DEFAULT_MAX_TEST_IDLE_TIME_SECS);
            configuredIdleTimeout = Long.valueOf(String.valueOf(idleTimeout));
        } catch (Exception e) {
            configuredIdleTimeout = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
            LOGGER.log(Level.WARNING, getId() + " " + e.toString(), e);
        }
        if (configuredIdleTimeout <= 0) {
            configuredIdleTimeout = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
        }
        return configuredIdleTimeout;
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        super.beforeCommand(session, request, response);
        LOGGER.log(Level.FINE,
                getId() + " lastCommand: " +  request.getMethod() + " - " + request.getPathInfo() + " executing...");
    }

    @Override
    public void afterCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        super.afterCommand(session, request, response);
        LOGGER.log(Level.FINE,
                getId() + " lastCommand: " +  request.getMethod() + " - " + request.getPathInfo() + " executed.");
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (RequestType.START_SESSION.equals(seleniumRequest.getRequestType())) {
                videoRecording(DockerSeleniumContainerAction.START_RECORDING);
            }
        }
    }

    @Override
    public void afterSession(TestSession session) {
        String message = String.format("%s AFTER_SESSION command received. Node should shutdown soon...", getId());
        LOGGER.log(Level.INFO, message);
        shutdownNode(false);
        long executionTime = (System.currentTimeMillis() - session.getSlot().getLastSessionStart()) / 1000;
        ga.testEvent(DockerSeleniumRemoteProxy.class.getName(), session.getRequestedCapabilities().toString(),
                executionTime);
        super.afterSession(session);
    }

    @Override
    public void startPolling() {
        super.startPolling();
        dockerSeleniumNodePollerThread = new DockerSeleniumNodePoller(this);
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

    private String getCapability(Map<String, Object> requestedCapability, String capabilityName, String defaultValue) {
        if (requestedCapability.containsKey(capabilityName) && requestedCapability.get(capabilityName) != null) {
            return requestedCapability.get(capabilityName).toString();
        }
        return defaultValue;
    }

    /*
        Method to decide if the node can be removed based on the amount of executed tests.
     */
    @VisibleForTesting
    protected synchronized boolean isTestSessionLimitReached() {
        return getAmountOfExecutedTests() >= MAX_UNIQUE_TEST_SESSIONS;
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
                LOGGER.log(Level.SEVERE, getId() + e.toString(), e);
                ga.trackException(e);
            }
        } else {
            String message = String.format("%s %s: Video recording is disabled", getId(), action.getContainerAction());
            LOGGER.log(Level.INFO, message);
        }
    }

    public String getTestName() {
        return testName == null ? "" : testName;
    }

    public String getTestGroup() {
        return testGroup == null ? "" : testGroup;
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
        boolean waitForExecution = DockerSeleniumContainerAction.STOP_RECORDING == action ||
                DockerSeleniumContainerAction.TRANSFER_LOGS == action;
        final String[] command = {"bash", "-c", action.getContainerAction()};
        containerClient.executeCommand(containerId, command, waitForExecution);

        if (waitForExecution && DockerSeleniumContainerAction.STOP_RECORDING == action) {
            copyVideos(containerId);
        }
        if (waitForExecution && DockerSeleniumContainerAction.TRANSFER_LOGS == action) {
            copyLogs(containerId);
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
                LOGGER.log(Level.INFO, "{0} Video file copied to: {1}/{2}", new Object[]{getId(),
                        testInformation.getVideoFolderPath(), testInformation.getFileName()});
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, getId() + " Error while copying the video", e);
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
            LOGGER.log(Level.INFO, "{0} Logs copied to: {1}", new Object[]{getId(), testInformation.getLogsFolderPath()});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, getId() + " Error while copying the logs", e);
            ga.trackException(e);
        }
    }

    private void shutdownNode(boolean isTestIdle) {
        videoRecording(DockerSeleniumContainerAction.STOP_RECORDING);
        processContainerAction(DockerSeleniumContainerAction.TRANSFER_LOGS, getContainerId());
        Dashboard.updateDashboard(testInformation);

        String shutdownReason = String.format("%s Marking the node as down because it was stopped after %s tests.",
                getId(), MAX_UNIQUE_TEST_SESSIONS);

        if (isTestIdle) {
            terminateIdleTest();
            shutdownReason = String.format("%s Marking the node as down because the test has been idle for more than %s seconds.",
                    getId(), getMaxTestIdleTimeSecs());
        }

        containerClient.stopContainer(getContainerId());
        addNewEvent(new RemoteNotReachableException(shutdownReason));
        addNewEvent(new RemoteUnregisterException(shutdownReason));
        teardown();
    }


    public enum DockerSeleniumContainerAction {
        START_RECORDING("start-video"), STOP_RECORDING("stop-video"), TRANSFER_LOGS("transfer-logs.sh");

        private String containerAction;

        DockerSeleniumContainerAction(String action) {
            containerAction = action;
        }

        public String getContainerAction() {
            return containerAction;
        }
    }

    /*
        Class to poll continuously the node status regarding the amount of tests executed. If MAX_UNIQUE_TEST_SESSIONS
        have been executed, then the node is removed from the grid (this should trigger the docker container to stop).
     */
    static class DockerSeleniumNodePoller extends Thread {

        private static long sleepTimeBetweenChecks = 500;
        private DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = null;

        DockerSeleniumNodePoller(DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy) {
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
                    LOGGER.log(Level.INFO, dockerSeleniumRemoteProxy.getId() +
                            " Shutting down node due to test inactivity");
                    dockerSeleniumRemoteProxy.shutdownNode(true);
                    return;
                }
                try {
                    Thread.sleep(getSleepTimeBetweenChecks());
                } catch (InterruptedException e) {
                    LOGGER.log(Level.FINE, dockerSeleniumRemoteProxy.getId() + " Error while sleeping the " +
                            "thread, stopping thread execution.", e);
                    return;
                }
            }
        }
    }


}

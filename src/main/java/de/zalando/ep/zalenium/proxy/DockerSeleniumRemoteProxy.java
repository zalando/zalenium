package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ExecCreation;
import de.zalando.ep.zalenium.util.*;
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
import java.net.URISyntaxException;
import java.util.List;
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
    private static final DockerClient defaultDockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    private static final Environment defaultEnvironment = new Environment();
    private static boolean videoRecordingEnabled;
    private static DockerClient dockerClient = defaultDockerClient;
    private static Environment env = defaultEnvironment;
    private int amountOfExecutedTests;
    private long maxTestIdleTimeSecs;
    private String testGroup;
    private String testName;
    private String containerId = null;
    private TestInformation testInformation;
    private boolean afterSessionEventReceived = false;
    private DockerSeleniumNodePoller dockerSeleniumNodePollerThread = null;
    private GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private CapabilityMatcher capabilityHelper;
    private final HtmlRenderer renderer = new WebProxyHtmlRendererBeta(this);

    public DockerSeleniumRemoteProxy(RegistrationRequest request, Registry registry) {
        super(request, registry);
        this.amountOfExecutedTests = 0;
        readEnvVarForVideoRecording();
    }

    public HtmlRenderer getHtmlRender() {
        return this.renderer;
    }

    @VisibleForTesting
    static void readEnvVarForVideoRecording() {
        boolean videoEnabled = env.getBooleanEnvVariable(ZALENIUM_VIDEO_RECORDING_ENABLED,
                DEFAULT_VIDEO_RECORDING_ENABLED);
        setVideoRecordingEnabled(videoEnabled);
    }

    @VisibleForTesting
    static void setDockerClient(final DockerClient client) {
        dockerClient = client;
    }

    @VisibleForTesting
    static void restoreDockerClient() {
        dockerClient = defaultDockerClient;
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
    protected static boolean isVideoRecordingEnabled() {
        return videoRecordingEnabled;
    }

    private static void setVideoRecordingEnabled(boolean videoRecordingEnabled) {
        DockerSeleniumRemoteProxy.videoRecordingEnabled = videoRecordingEnabled;
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
            String browserName = requestedCapability.getOrDefault(CapabilityType.BROWSER_NAME, "").toString();
            testName = requestedCapability.getOrDefault("name", "").toString();
            if (testName.isEmpty()) {
                testName = newSession.getExternalKey() != null ?
                        newSession.getExternalKey().getKey() :
                        newSession.getInternalKey();
            }
            testGroup = requestedCapability.getOrDefault("group", "").toString();
            if (requestedCapability.containsKey("recordVideo")) {
                boolean videoRecording = Boolean.parseBoolean(requestedCapability.get("recordVideo").toString());
                setVideoRecordingEnabled(videoRecording);
            }
            String browserVersion = newSession.getSlot().getCapabilities().getOrDefault("version", "").toString();
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
            configuredIdleTimeout = (long) requestedCapability.getOrDefault("idleTimeout", DEFAULT_MAX_TEST_IDLE_TIME_SECS);
        } catch (Exception e) {
            configuredIdleTimeout = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
            LOGGER.log(Level.FINE, getId() + " " + e.toString(), e);
        }
        if (configuredIdleTimeout <= 0) {
            configuredIdleTimeout = DEFAULT_MAX_TEST_IDLE_TIME_SECS;
        }
        return configuredIdleTimeout;
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (RequestType.START_SESSION.equals(seleniumRequest.getRequestType())) {
                videoRecording(DockerSeleniumContainerAction.START_RECORDING);
            }
        }
        super.beforeCommand(session, request, response);
    }

    @Override
    public void afterSession(TestSession session) {
        this.afterSessionEventReceived = true;
        String message = String.format("%s AFTER_SESSION command received. Node should shutdown soon...", getId());
        LOGGER.log(Level.INFO, message);
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
        return maxTestIdleTimeSecs;
    }

    protected String getContainerId() throws DockerException, InterruptedException {
        if (containerId == null) {
            List<Container> containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
            for (Container container : containerList) {
                String containerName = String.format("/%s_%s", DockerSeleniumStarterRemoteProxy.getContainerName(),
                        getRemoteHost().getPort());
                if (containerName.equalsIgnoreCase(container.names().get(0))) {
                    containerId = container.id();
                }
            }
        }
        return containerId;
    }

    @VisibleForTesting
    void processContainerAction(final DockerSeleniumContainerAction action, final String containerId) throws
            DockerException, InterruptedException, IOException, URISyntaxException {
        final String[] command = {"bash", "-c", action.getContainerAction()};
        final ExecCreation execCreation = dockerClient.execCreate(containerId, command,
                DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
        final LogStream output = dockerClient.execStart(execCreation.id());
        LOGGER.log(Level.INFO, () -> String.format("%s %s", getId(), action.getContainerAction()));
        try {
            LOGGER.log(Level.INFO, () -> String.format("%s %s", getId(), output.readFully()));
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, getId() + " " + e.toString(), e);
            ga.trackException(e);
        }

        if (DockerSeleniumContainerAction.STOP_RECORDING == action) {
            copyVideos(containerId);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @VisibleForTesting
    void copyVideos(final String containerId) throws IOException, DockerException, InterruptedException, URISyntaxException {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(dockerClient.archiveContainer(containerId,
                "/videos/"))) {
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
                LOGGER.log(Level.INFO, "{0} Video file copied to: {1}/{2}", new Object[]{getId(),
                        testInformation.getVideoFolderPath(), testInformation.getFileName()});
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, getId() + " Something happened while copying the video file, " +
                    "most of the time it is an issue while closing the input/output stream, which is usually OK.", e);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @VisibleForTesting
    void copyLogs(final String containerId) throws IOException, DockerException, InterruptedException, URISyntaxException {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(dockerClient.archiveContainer(containerId,
                "/var/log/cont/"))) {
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
        } catch (Exception e) {
            LOGGER.log(Level.FINE, getId() + " Something happened while copying the log file, " +
                    "most of the time it is an issue while closing the input/output stream, which is usually OK.", e);
        }
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
                    If the proxy is not busy and it can be released since the MAX_UNIQUE_TEST_SESSIONS have been executed,
                    then the node executes its teardown.
                    OR
                    If the current session has been idle for a while, the node shuts down
                */
                boolean isTestCompleted = !dockerSeleniumRemoteProxy.isBusy()
                        && dockerSeleniumRemoteProxy.isTestSessionLimitReached()
                        && dockerSeleniumRemoteProxy.afterSessionEventReceived;
                boolean isTestIdle = dockerSeleniumRemoteProxy.isTestIdle();

                if (isTestCompleted || isTestIdle) {
                    dockerSeleniumRemoteProxy.videoRecording(DockerSeleniumContainerAction.STOP_RECORDING);
                    try {
                        dockerSeleniumRemoteProxy.processContainerAction(DockerSeleniumContainerAction.TRANSFER_LOGS,
                                dockerSeleniumRemoteProxy.getContainerId());
                        dockerSeleniumRemoteProxy.copyLogs(dockerSeleniumRemoteProxy.getContainerId());
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, dockerSeleniumRemoteProxy.getId() + " Error copying the logs.", e);
                    }
                    try {
                        DashboardDataHandler.addNewTest(dockerSeleniumRemoteProxy.testInformation);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, dockerSeleniumRemoteProxy.getId() + " Error while updating the " +
                                "dashboard.", e);
                    }
                    shutdownNode(isTestIdle);
                    return;
                }

                try {
                    Thread.sleep(getSleepTimeBetweenChecks());
                } catch (InterruptedException e) {
                    LOGGER.log(Level.FINE, dockerSeleniumRemoteProxy.getId() + " Error while sleeping the " +
                            "thread, stopping thread execution.", e);
                    Thread.currentThread().interrupt();
                    dockerSeleniumRemoteProxy.ga.trackException(e);
                    dockerSeleniumRemoteProxy.stopPolling();
                    dockerSeleniumRemoteProxy.startPolling();
                    return;
                }
            }
        }

        private void shutdownNode(boolean isTestIdle) {
            String shutdownReason = String.format("%s Marking the node as down because it was stopped after %s tests.",
                    dockerSeleniumRemoteProxy.getId(), MAX_UNIQUE_TEST_SESSIONS);

            if (isTestIdle) {
                dockerSeleniumRemoteProxy.terminateIdleTest();
                shutdownReason = String.format("%s Marking the node as down because the test has been idle for more than %s seconds.",
                        dockerSeleniumRemoteProxy.getId(), dockerSeleniumRemoteProxy.getMaxTestIdleTimeSecs());
            }

            try {
                dockerClient.stopContainer(dockerSeleniumRemoteProxy.getContainerId(), 5);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, dockerSeleniumRemoteProxy.getId() + " " + e.getMessage(), e);
                dockerSeleniumRemoteProxy.ga.trackException(e);
            } finally {
                dockerSeleniumRemoteProxy.addNewEvent(new RemoteNotReachableException(shutdownReason));
                dockerSeleniumRemoteProxy.addNewEvent(new RemoteUnregisterException(shutdownReason));
                dockerSeleniumRemoteProxy.teardown();
            }
        }

    }


}

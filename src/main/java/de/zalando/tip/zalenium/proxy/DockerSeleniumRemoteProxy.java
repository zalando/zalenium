package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ExecCreation;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.RemoteNotReachableException;
import org.openqa.grid.common.exception.RemoteUnregisterException;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;

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
public class DockerSeleniumRemoteProxy extends DefaultRemoteProxy {

    private static final Logger LOGGER = Logger.getLogger(DockerSeleniumRemoteProxy.class.getName());

    // Amount of tests that can be executed in the node
    private static final int MAX_UNIQUE_TEST_SESSIONS = 1;

    private int amountOfExecutedTests;

    private DockerSeleniumNodePoller dockerSeleniumNodePollerThread = null;

    private static final DockerClient defaultDockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    private static DockerClient dockerClient = defaultDockerClient;

    public enum VideoRecordingAction {
        START_RECORDING("start-video"), STOP_RECORDING("stop-video");

        private String recordingAction;

        VideoRecordingAction(String action) {
            recordingAction = action;
        }

        public String getRecordingAction() {
            return recordingAction;
        }
    }


    public DockerSeleniumRemoteProxy(RegistrationRequest request, Registry registry) {
        super(request, registry);
        this.amountOfExecutedTests = 0;
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
            videoRecording(VideoRecordingAction.START_RECORDING);
            return newSession;
        }
        LOGGER.log(Level.FINE, "{0} No more sessions allowed", getNodeIpAndPort());
        return null;
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

    public String getNodeUrl() {
        return "http://" + getRemoteHost().getHost() + ":" + getRemoteHost().getPort();
    }

    public String getNodeIpAndPort() { return getRemoteHost().getHost() + ":" + getRemoteHost().getPort(); }

    /*
        Incrementing variable to count the number of tests executed, if possible.
     */
    private synchronized boolean increaseCounter(){
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
    public synchronized boolean isTestSessionLimitReached() {
        return getAmountOfExecutedTests() >= MAX_UNIQUE_TEST_SESSIONS;
    }

    public int getAmountOfExecutedTests() {
        return amountOfExecutedTests;
    }

    public void videoRecording(final VideoRecordingAction action) {
        try {
            String containerId = getContainerId();
            processVideoAction(action, containerId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, getNodeIpAndPort() + e.toString(), e);
        }
    }

    protected String getContainerId() throws DockerException, InterruptedException {
        List<Container> containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
        for (Container container : containerList) {
            String containerName = "/zalenium_" + getRemoteHost().getPort();
            if (containerName.equalsIgnoreCase(container.names().get(0))) {
                return container.id();
            }
        }
        return null;
    }

    @VisibleForTesting
    protected void processVideoAction(final VideoRecordingAction action, final String containerId) throws
            DockerException, InterruptedException, IOException, URISyntaxException {
        final String[] command = {"bash", "-c", action.getRecordingAction()};
        final ExecCreation execCreation = dockerClient.execCreate(containerId, command,
                DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
        final LogStream output = dockerClient.execStart(execCreation.id());
        LOGGER.log(Level.INFO, "{0} {1}", new Object[]{getNodeIpAndPort(), action.getRecordingAction()});
        try {
            LOGGER.log(Level.INFO, "{0} {1}", new Object[]{getNodeIpAndPort(), output.readFully()});
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, getNodeIpAndPort() + " " + e.toString(), e);
        }

        if (VideoRecordingAction.STOP_RECORDING == action) {
            copyVideos(containerId);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @VisibleForTesting
    protected void copyVideos(final String containerId) throws IOException, DockerException, InterruptedException, URISyntaxException {
        File jarLocation = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        String localPath = jarLocation.getParent();
        try(TarArchiveInputStream tarStream = new TarArchiveInputStream(dockerClient.archiveContainer(containerId,
                "/videos/"))) {
            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File curFile = new File(localPath, entry.getName());
                File parent = curFile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                OutputStream outputStream = new FileOutputStream(curFile);
                IOUtils.copy(tarStream, outputStream);
                outputStream.close();
            }
        }
        LOGGER.log(Level.INFO, "{0} Video files copies to: {1}", new Object[]{getNodeIpAndPort(), localPath});
    }

    @VisibleForTesting
    protected static void setDockerClient(final DockerClient client) {
        dockerClient = client;
    }

    @VisibleForTesting
    protected static void restoreDockerClient() {
        dockerClient = defaultDockerClient;
    }

    public DockerSeleniumNodePoller getDockerSeleniumNodePollerThread() {
        return dockerSeleniumNodePollerThread;
    }

    /*
        Class to poll continuously the node status regarding the amount of tests executed. If MAX_UNIQUE_TEST_SESSIONS
        have been executed, then the node is removed from the grid (this should trigger the docker container to stop).
     */
    static class DockerSeleniumNodePoller extends Thread {

        private DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = null;
        private HttpClient client;
        private static long sleepTimeBetweenChecks = 500;

        public DockerSeleniumNodePoller(DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy) {
            this.dockerSeleniumRemoteProxy = dockerSeleniumRemoteProxy;
        }

        public long getSleepTimeBetweenChecks() {
            return sleepTimeBetweenChecks;
        }


        @VisibleForTesting
        protected void setClient(HttpClient client) {
            this.client = client;
        }

        @Override
        public void run() {
            while (true) {
                /*
                    If the proxy is not busy and it can be released since the MAX_UNIQUE_TEST_SESSIONS have been executed,
                    then the node executes its teardown.
                */
                if (!dockerSeleniumRemoteProxy.isBusy() && dockerSeleniumRemoteProxy.isTestSessionLimitReached()) {
                    dockerSeleniumRemoteProxy.videoRecording(VideoRecordingAction.STOP_RECORDING);
                    shutdownNode();
                    return;
                }

                try {
                    Thread.sleep(getSleepTimeBetweenChecks());
                } catch (InterruptedException e) {
                    LOGGER.log(Level.SEVERE, dockerSeleniumRemoteProxy.getNodeIpAndPort() + " Error while sleeping the " +
                            "thread, stopping thread execution.", e);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void shutdownNode() {
            String shutdownReason = String.format("%s Marking the node as down because it was stopped after %s tests.",
                        dockerSeleniumRemoteProxy.getNodeIpAndPort(), MAX_UNIQUE_TEST_SESSIONS);

            if (client == null) {
                client = HttpClientBuilder.create().build();
            }

            String shutDownUrl = dockerSeleniumRemoteProxy.getNodeUrl() +
                    "/selenium-server/driver/?cmd=shutDownSeleniumServer";
            HttpPost post = new HttpPost(shutDownUrl);
            try {
                client.execute(post);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, dockerSeleniumRemoteProxy.getNodeIpAndPort() + " " + e.getMessage(), e);
            } finally {
                dockerSeleniumRemoteProxy.addNewEvent(new RemoteNotReachableException(shutdownReason));
                dockerSeleniumRemoteProxy.addNewEvent(new RemoteUnregisterException(shutdownReason));
                dockerSeleniumRemoteProxy.teardown();
            }
        }

    }




}

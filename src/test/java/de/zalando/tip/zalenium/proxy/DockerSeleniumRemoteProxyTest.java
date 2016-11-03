package de.zalando.tip.zalenium.proxy;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ExecCreation;
import de.zalando.tip.zalenium.util.TestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.to;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;

public class DockerSeleniumRemoteProxyTest {

    private static final Logger LOGGER = Logger.getLogger(DockerSeleniumRemoteProxyTest.class.getName());
    private DockerSeleniumRemoteProxy proxy;
    private Registry registry;

    @Before
    public void setup() throws DockerException, InterruptedException, IOException {
        registry = Registry.newInstance();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        request.getCapabilities().clear();
        request.getCapabilities().addAll(DockerSeleniumStarterRemoteProxy.getDockerSeleniumFallbackCapabilities());

        // Creating the proxy
        proxy = DockerSeleniumRemoteProxy.getNewInstance(request, registry);

        DockerClient dockerClient = mock(DockerClient.class);
        ExecCreation execCreation = mock(ExecCreation.class);
        LogStream logStream = mock(LogStream.class);
        when(logStream.readFully()).thenReturn("ANY_STRING");
        when(execCreation.id()).thenReturn("ANY_ID");
        when(dockerClient.execCreate(anyString(), any(String[].class), any(DockerClient.ExecCreateParam.class),
                any(DockerClient.ExecCreateParam.class))).thenReturn(execCreation);
        when(dockerClient.execStart(anyString())).thenReturn(logStream);

        DockerSeleniumRemoteProxy.setDockerClient(dockerClient);
    }

    @After
    public void tearDown() {
        DockerSeleniumRemoteProxy.restoreDockerClient();
    }

    @Test
    public void dockerSeleniumOnlyRunsOneTestPerContainer() {
        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        // Not tests have been executed.
        Assert.assertEquals(0, proxy.getAmountOfExecutedTests());

        TestSession newSession = proxy.getNewSession(requestedCapability);

        Assert.assertNotNull(newSession);

        // One test is/has been executed and the session amount limit was reached.
        Assert.assertEquals(1, proxy.getAmountOfExecutedTests());
        Assert.assertTrue(proxy.isTestSessionLimitReached());
    }

    @Test
    public void secondRequestGetsANullTestRequest() {
        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        {
            TestSession newSession = proxy.getNewSession(requestedCapability);
            Assert.assertNotNull(newSession);
        }

        // Since only one test should be executed, the second request should come null
        {
            TestSession newSession = proxy.getNewSession(requestedCapability);
            Assert.assertNull(newSession);
        }
    }

    @Test
    public void noSessionIsCreatedWhenCapabilitiesAreNotSupported() {
        // Non supported capabilities
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN10);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNull(newSession);
    }

    @Test
    public void pollerThreadTearsDownNodeAfterTestIsCompleted() throws IOException {

        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        // Start poller thread
        proxy.startPolling();

        // Mock the poller HttpClient to avoid exceptions due to failed connections
        proxy.getDockerSeleniumNodePollerThread().setClient(getMockedClient());

        // Get a test session
        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);

        // The node should be busy since there is a session in it
        Assert.assertTrue(proxy.isBusy());

        // We release the session, the node should be free
        newSession.getSlot().doFinishRelease();

        // After running one test, the node shouldn't be busy and also down
        Assert.assertFalse(proxy.isBusy());
        long sleepTime = proxy.getDockerSeleniumNodePollerThread().getSleepTimeBetweenChecks();
        await().atMost(sleepTime + 2000, MILLISECONDS).untilCall(to(proxy).isDown(), equalTo(true));
    }

    @Test
    public void videoRecordingIsStartedAndStopped() throws DockerException, InterruptedException,
            URISyntaxException, IOException {

        DockerClient dockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
        String containerId = null;
        String zaleniumContainerId = null;
        String busyboxLatestImage = "busybox:latest";
        try {
            // Removing first all docker-selenium containers
            List<Container> containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
            for (Container container : containerList) {
                String containerName = "zalenium";
                if (container.names().get(0).contains(containerName)) {
                    dockerClient.stopContainer(container.id(), 5);
                    dockerClient.removeContainer(container.id());
                }
            }

            // We create another container first with the name "zalenium", so the container creation in the
            // next step works
            dockerClient.pull(busyboxLatestImage);
            final ContainerConfig containerConfig = ContainerConfig.builder()
                    .image(busyboxLatestImage)
                    // make sure the container's busy doing something upon startup
                    .cmd("sh", "-c", "while :; do sleep 1; done")
                    .build();
            final ContainerCreation containerCreation = dockerClient.createContainer(containerConfig, "zalenium");
            zaleniumContainerId = containerCreation.id();
            dockerClient.startContainer(zaleniumContainerId);

            // Create a docker-selenium container
            RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30000,
                    DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
            DockerSeleniumStarterRemoteProxy dsProxy = new DockerSeleniumStarterRemoteProxy(request, registry);
            DockerSeleniumStarterRemoteProxy.setMaxDockerSeleniumContainers(1);
            dsProxy.getNewSession(getCapabilitySupportedByDockerSelenium());

            // Creating a spy proxy to verify the invoked methods
            DockerSeleniumRemoteProxy spyProxy = spy(proxy);
            DockerSeleniumRemoteProxy.setDockerClient(dockerClient);

            // Wait for the container to be ready
            containerId = spyProxy.getContainerId();
            LOGGER.info(zaleniumContainerId);
            LOGGER.info(containerId);
            final String[] command = {"bash", "-c", "wait_all_done 30s"};
            final ExecCreation execCreation = dockerClient.execCreate(containerId, command,
                    DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
            dockerClient.execStart(execCreation.id());

            // Waiting until the container is ready
            final String finalContainerId = containerId;
            Callable<Boolean> callable = () ->
                    !dockerClient.topContainer(finalContainerId).processes().toString().contains("wait_all_done");
            await().atMost(40, SECONDS).pollInterval(2, SECONDS).until(callable);

            // Start poller thread
            spyProxy.startPolling();

            // Mock the poller HttpClient to avoid exceptions due to failed connections
            spyProxy.getDockerSeleniumNodePollerThread().setClient(getMockedClient());

            // Get a test session
            TestSession newSession = spyProxy.getNewSession(getCapabilitySupportedByDockerSelenium());
            Assert.assertNotNull(newSession);

            // Assert video recording started
            verify(spyProxy, times(1)).videoRecording(DockerSeleniumRemoteProxy.VideoRecordingAction.START_RECORDING);
            verify(spyProxy, times(1)).processVideoAction(DockerSeleniumRemoteProxy.VideoRecordingAction.START_RECORDING,
                    containerId);

            // We release the sessions, the node should be free
            newSession.getSlot().doFinishRelease();

            Assert.assertFalse(spyProxy.isBusy());
            verify(spyProxy, timeout(40000))
                    .videoRecording(DockerSeleniumRemoteProxy.VideoRecordingAction.STOP_RECORDING);
            verify(spyProxy, timeout(40000))
                    .processVideoAction(DockerSeleniumRemoteProxy.VideoRecordingAction.STOP_RECORDING, containerId);
            verify(spyProxy, timeout(40000)).copyVideos(containerId);
        } finally {
            DockerSeleniumStarterRemoteProxy.setMaxDockerSeleniumContainers(0);
            if (containerId != null) {
                dockerClient.stopContainer(containerId, 5);
                dockerClient.removeContainer(containerId);
            }
            if (zaleniumContainerId != null) {
                dockerClient.stopContainer(zaleniumContainerId, 5);
                dockerClient.removeContainer(zaleniumContainerId);
            }
            dockerClient.removeImage(busyboxLatestImage);
        }
    }

    private Map<String, Object> getCapabilitySupportedByDockerSelenium() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        return requestedCapability;
    }

    private HttpClient getMockedClient() throws IOException {
        HttpClient client = mock(HttpClient.class);
        HttpResponse httpResponse = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(client.execute(any(HttpPost.class))).thenReturn(httpResponse);
        return client;
    }


}

package de.zalando.tip.zalenium.proxy;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ExecCreation;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.GridRole;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.spotify.docker.client.DockerClient.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.*;

public class DockerSeleniumRemoteProxyTest {

    private DockerSeleniumRemoteProxy proxy;

    @Before
    public void setup() {
        Registry registry = Registry.newInstance();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = new RegistrationRequest();
        request.setRole(GridRole.NODE);
        request.getConfiguration().put(RegistrationRequest.MAX_SESSION, 5);
        request.getConfiguration().put(RegistrationRequest.AUTO_REGISTER, true);
        request.getConfiguration().put(RegistrationRequest.REGISTER_CYCLE, 5000);
        request.getConfiguration().put(RegistrationRequest.HUB_HOST, "localhost");
        request.getConfiguration().put(RegistrationRequest.HUB_PORT, 4444);
        request.getConfiguration().put(RegistrationRequest.PORT, 40000);
        request.getConfiguration().put(RegistrationRequest.PROXY_CLASS, "de.zalando.tip.zalenium.proxy.DockerSeleniumRemoteProxy");
        request.getConfiguration().put(RegistrationRequest.REMOTE_HOST, "http://localhost:4444");
        request.getCapabilities().clear();
        request.getCapabilities().addAll(DockerSeleniumStarterRemoteProxy.getDockerSeleniumFallbackCapabilities());

        // Creating the proxy
        proxy = DockerSeleniumRemoteProxy.getNewInstance(request, registry);
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
    public void pollerThreadTearsDownNodeAfterTestIsCompleted() throws InterruptedException, IOException {
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
        await().atMost(sleepTime + 1, MILLISECONDS).untilCall(to(proxy).isDown(), equalTo(true));
    }

    @Test
    public void videoRecordingIsStartedAndStopped() throws IOException, DockerException, InterruptedException,
            URISyntaxException {
        try {
            // Creating a spy proxy to verify the invoked methods
            DockerSeleniumRemoteProxy spyProxy = spy(proxy);

            // Mock the docker client to get some listed containers
            DockerClient dockerClient = mock(DockerClient.class);
            Container dockerContainer = mock(Container.class);
            List<String> containerNames = new ArrayList<>();
            containerNames.add("/ZALENIUM_4444");

            when(dockerContainer.names()).thenReturn(containerNames);
            when(dockerContainer.id()).thenReturn("ZALENIUM_CONTAINER_ID");

            List<Container> containerList = new ArrayList<>();
            containerList.add(dockerContainer);
            ExecCreation execCreation = mock(ExecCreation.class);
            LogStream logStream = mock(LogStream.class);

            when(execCreation.id()).thenReturn(anyString());
            when(dockerClient.listContainers(ListContainersParam.allContainers())).thenReturn(containerList);
            when(dockerClient.execCreate(anyString(), any(String[].class), any(ExecCreateParam.class),
                    any(ExecCreateParam.class)))
                    .thenReturn(execCreation);
            when(logStream.readFully()).thenReturn("");
            when(dockerClient.execStart(execCreation.id())).thenReturn(logStream);

            TarArchiveInputStream tarArchiveInputStream = mock(TarArchiveInputStream.class);
            TarArchiveEntry directoryEntry = mock(TarArchiveEntry.class);
            TarArchiveEntry fileEntry = mock(TarArchiveEntry.class);
            when(directoryEntry.isDirectory()).thenReturn(true);
            when(fileEntry.getName()).thenReturn("file.mkv");
            when(tarArchiveInputStream.getNextTarEntry()).thenReturn(directoryEntry);
            when(dockerClient.archiveContainer(dockerContainer.id(), "/videos/")).thenReturn(tarArchiveInputStream);

            DockerSeleniumRemoteProxy.setDockerClient(dockerClient);

            // Supported desired capability for the test session
            Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

            // Start poller thread
            spyProxy.startPolling();

            // Mock the poller HttpClient to avoid exceptions due to failed connections
            spyProxy.getDockerSeleniumNodePollerThread().setClient(getMockedClient());

            // Get a test session
            TestSession newSession = spyProxy.getNewSession(requestedCapability);
            Assert.assertNotNull(newSession);

            // Assert video recording started
            verify(spyProxy, times(1)).videoRecording(DockerSeleniumRemoteProxy.VideoRecordingAction.START_RECORDING);
            verify(spyProxy, times(1)).processVideoAction(DockerSeleniumRemoteProxy.VideoRecordingAction.START_RECORDING,
                    dockerContainer.id());

            // We release the sessions, the node should be free
            spyProxy.getTestSlots().forEach(TestSlot::doFinishRelease);

            Assert.assertFalse(spyProxy.isBusy());
            long sleepTime = spyProxy.getDockerSeleniumNodePollerThread().getSleepTimeBetweenChecks();
            verify(spyProxy, timeout(sleepTime + 1000))
                    .videoRecording(DockerSeleniumRemoteProxy.VideoRecordingAction.STOP_RECORDING);
            verify(spyProxy, timeout(sleepTime + 1000)).copyVideos(dockerContainer.id());
        } finally {
            DockerSeleniumRemoteProxy.restoreDockerClient();
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

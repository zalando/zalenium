package de.zalando.ep.zalenium.proxy;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletResponse;

import de.zalando.ep.zalenium.container.DockerContainerClient;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.container.kubernetes.KubernetesContainerClient;
import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.KubernetesContainerMock;
import de.zalando.ep.zalenium.util.TestUtils;


@SuppressWarnings("Duplicates")
@RunWith(value = Parameterized.class)
public class DockerSeleniumRemoteProxyTest {

    private DockerSeleniumRemoteProxy proxy;
    private GridRegistry registry;
    private ContainerClient containerClient;
    private DockerContainerClient originalDockerContainerClient;
    private KubernetesContainerClient originalKubernetesContainerClient;
    private Supplier<Boolean> originalIsKubernetesValue;
    private Supplier<Boolean> currentIsKubernetesValue;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    public DockerSeleniumRemoteProxyTest(ContainerClient containerClient, Supplier<Boolean> isKubernetes) {
        this.containerClient = containerClient;
        this.currentIsKubernetesValue = isKubernetes;
        this.originalDockerContainerClient = ContainerFactory.getDockerContainerClient();
        this.originalIsKubernetesValue = ContainerFactory.getIsKubernetes();
        this.originalKubernetesContainerClient = ContainerFactory.getKubernetesContainerClient();
    }

    @Parameters
    public static Collection<Object[]> data() {
        Supplier<Boolean> bsFalse = () -> false;
        Supplier<Boolean> bsTrue = () -> true;
        return Arrays.asList(new Object[][] {
                {DockerContainerMock.getMockedDockerContainerClient(), bsFalse},
                {DockerContainerMock.getMockedDockerContainerClient("host"), bsFalse},
                {KubernetesContainerMock.getMockedKubernetesContainerClient(), bsTrue}
        });
    }

    @Before
    public void setUp() {
        // Change the factory to return our version of the Container Client
        if (this.currentIsKubernetesValue.get()) {
            // This is needed in order to use a fresh version of the mock, otherwise the return values
            // are gone, and returning them always is not the normal behaviour.
            this.containerClient = KubernetesContainerMock.getMockedKubernetesContainerClient();
            ContainerFactory.setKubernetesContainerClient((KubernetesContainerClient) containerClient);
        } else {
            this.containerClient = DockerContainerMock.getMockedDockerContainerClient();
            ContainerFactory.setDockerContainerClient((DockerContainerClient) containerClient);
        }
        ContainerFactory.setIsKubernetes(this.currentIsKubernetesValue);

        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
            new JMXHelper().unregister(objectName);
        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
            // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
        }

        registry = new de.zalando.ep.zalenium.util.SimpleRegistry();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        request.getConfiguration().capabilities.clear();
        request.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());

        // Creating the proxy
        proxy = DockerSeleniumRemoteProxy.getNewInstance(request, registry);

        proxy.setContainerClient(containerClient);
    }

    @After
    public void tearDown() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40000\"");
        new JMXHelper().unregister(objectName);
        ContainerFactory.setDockerContainerClient(originalDockerContainerClient);
        ContainerFactory.setIsKubernetes(originalIsKubernetesValue);
        ContainerFactory.setKubernetesContainerClient(originalKubernetesContainerClient);
        proxy.restoreContainerClient();
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
        requestedCapability.put("name", "anyRandomTestName");
        requestedCapability.put("build", "anyRandomTestBuild");

        {
            TestSession newSession = proxy.getNewSession(requestedCapability);
            Assert.assertNotNull(newSession);
        }

        // Since only one test should be executed, the second request should come null
        {
            TestSession newSession = proxy.getNewSession(requestedCapability);
            Assert.assertNull(newSession);
        }

        Assert.assertEquals("anyRandomTestBuild", proxy.getTestBuild());
        Assert.assertEquals("anyRandomTestName", proxy.getTestName());
    }

    @Test
    public void sessionGetsCreatedEvenIfCapabilitiesAreNull() {
        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("name", null);
        requestedCapability.put("build", null);
        requestedCapability.put("version", null);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);

        Assert.assertTrue(proxy.getTestBuild().isEmpty());
        Assert.assertEquals(newSession.getInternalKey(), proxy.getTestName());
    }

    @Test
    public void noSessionIsCreatedWhenCapabilitiesAreNotSupported() {
        // Non supported capabilities
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN10);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNull(newSession);
    }

    @Test
    public void noSessionIsCreatedWithSpecialScreenSize() {
        // Non supported capabilities
        Dimension customScreenSize = new Dimension(1280, 760);
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        String screenResolution = String.format("%sx%s", customScreenSize.getWidth(), customScreenSize.getHeight());
        requestedCapability.put("screenResolution", screenResolution);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNull(newSession);
    }

    @Test
    public void noSessionIsCreatedWithSpecialTimeZone() {
        // Non supported capabilities
        TimeZone timeZone = TimeZone.getTimeZone("America/Montreal");
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("tz", timeZone.getID());

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNull(newSession);
    }

    @Test
    public void testIdleTimeoutUsesDefaultValueWhenCapabilityIsNotPresent() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);
        Assert.assertEquals(proxy.getMaxTestIdleTimeSecs(), DockerSeleniumRemoteProxy.DEFAULT_MAX_TEST_IDLE_TIME_SECS);
    }

    @Test
    public void testIdleTimeoutUsesDefaultValueWhenCapabilityHasNegativeValue() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("idleTimeout", -20L);

        proxy.getNewSession(requestedCapability);
        Assert.assertEquals(proxy.getMaxTestIdleTimeSecs(), DockerSeleniumRemoteProxy.DEFAULT_MAX_TEST_IDLE_TIME_SECS);
    }

    @Test
    public void testIdleTimeoutUsesDefaultValueWhenCapabilityHasFaultyValue() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("zal:idleTimeout", "thisValueIsNAN Should not work.");

        proxy.getNewSession(requestedCapability);
        Assert.assertEquals(proxy.getMaxTestIdleTimeSecs(), DockerSeleniumRemoteProxy.DEFAULT_MAX_TEST_IDLE_TIME_SECS);
    }

    @Test
    public void testIdleTimeoutUsesValueInStringPassedAsCapability() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("zal:idleTimeout", "200");

        proxy.getNewSession(requestedCapability);
        Assert.assertEquals(200L, proxy.getMaxTestIdleTimeSecs());
    }

    @Test
    public void testIdleTimeoutUsesValuePassedAsCapability() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("zal:idleTimeout", 180L);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);
        Assert.assertEquals(180L, proxy.getMaxTestIdleTimeSecs());
    }

    @Test
    public void pollingThreadTearsDownNodeAfterTestIsCompleted() throws IOException {

        try {
            CommonProxyUtilities commonProxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            TestUtils.ensureRequiredInputFilesExist(temporaryFolder);
            Dashboard.setCommonProxyUtilities(commonProxyUtilities);

            // Supported desired capability for the test session
            Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

            // Start polling thread
            proxy.startPolling();

            // Get a test session
            TestSession newSession = proxy.getNewSession(requestedCapability);
            Assert.assertNotNull(newSession);

            // The node should be busy since there is a session in it
            Assert.assertTrue(proxy.isBusy());

            // We release the session, the node should be free
            WebDriverRequest request = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(request.getMethod()).thenReturn("DELETE");
            when(request.getRequestType()).thenReturn(RequestType.STOP_SESSION);

            newSession.getSlot().doFinishRelease();
            proxy.afterCommand(newSession, request, response);
            proxy.afterSession(newSession);

            // After running one test, the node shouldn't be busy and also down
            Assert.assertFalse(proxy.isBusy());
            Callable<Boolean> callable = () -> registry.getProxyById(proxy.getId()) == null;
            await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.TWO_SECONDS).until(callable);
        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }

    @Test
    public void normalSessionCommandsDoNotStopNode() {

        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        // Start polling thread
        proxy.startPolling();

        // Get a test session
        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);

        // The node should be busy since there is a session in it
        Assert.assertTrue(proxy.isBusy());

        // We release the session, the node should be free
        WebDriverRequest request = mock(WebDriverRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestType()).thenReturn(RequestType.REGULAR);

        proxy.afterCommand(newSession, request, response);

        // The node should not tear down
        Assert.assertTrue(proxy.isBusy());
        Callable<Boolean> callable = () -> !proxy.isDown();
        await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.TWO_SECONDS).until(callable);
    }

    @Test
    public void testIsMarkedAsPassedAndFailedWithCookie() {

        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        // Start polling thread
        proxy.startPolling();

        // Get a test session
        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);

        // The node should be busy since there is a session in it
        Assert.assertTrue(proxy.isBusy());

        // We release the session, the node should be free
        WebDriverRequest request = mock(WebDriverRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestType()).thenReturn(RequestType.REGULAR);
        when(request.getPathInfo()).thenReturn("/cookie");
        when(request.getBody()).thenReturn("{\"cookie\": {\"name\": \"zaleniumTestPassed\", \"value\": true}}");

        proxy.beforeCommand(newSession, request, response);
        Assert.assertEquals(TestInformation.TestStatus.SUCCESS, proxy.getTestInformation().getTestStatus());

        when(request.getBody()).thenReturn("{\"cookie\": {\"name\": \"zaleniumTestPassed\", \"value\": false}}");

        proxy.beforeCommand(newSession, request, response);
        Assert.assertEquals(TestInformation.TestStatus.FAILED, proxy.getTestInformation().getTestStatus());
    }

    @Test
    public void nodeShutsDownWhenTestIsIdle() {

        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("idleTimeout", 1L);

        DockerSeleniumRemoteProxy spyProxy = spy(proxy);

        // Start pulling thread
        spyProxy.startPolling();

        // Get a test session
        TestSession newSession = spyProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);

        // Start the session
        WebDriverRequest webDriverRequest = mock(WebDriverRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(webDriverRequest.getMethod()).thenReturn("POST");
        when(webDriverRequest.getRequestType()).thenReturn(RequestType.START_SESSION);
        when(webDriverRequest.getPathInfo()).thenReturn("/something");
        spyProxy.beforeCommand(newSession, webDriverRequest, response);

        // The node should be busy since there is a session in it
        Assert.assertTrue(spyProxy.isBusy());

        // The node should tear down after the maximum idle time is elapsed
        Assert.assertTrue(spyProxy.isBusy());
        Callable<Boolean> callable = () -> registry.getProxyById(spyProxy.getId()) == null;
        await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.FIVE_SECONDS).until(callable);
    }

    @Test
    public void fallbackToDefaultValueWhenEnvVariableIsNotABoolean() {
        try {
            Environment environment = mock(Environment.class, withSettings().useConstructor());
            when(environment.getEnvVariable(DockerSeleniumRemoteProxy.ZALENIUM_VIDEO_RECORDING_ENABLED))
                    .thenReturn("any_nonsense_value");
            when(environment.getBooleanEnvVariable(any(String.class), any(Boolean.class))).thenCallRealMethod();
            DockerSeleniumRemoteProxy.setEnv(environment);
            DockerSeleniumRemoteProxy.readEnvVars();

            Assert.assertEquals(DockerSeleniumRemoteProxy.DEFAULT_VIDEO_RECORDING_ENABLED,
                    proxy.isVideoRecordingEnabled());
        } finally {
            DockerSeleniumRemoteProxy.restoreEnvironment();
        }
    }

    @Test
    public void videoRecordingIsStartedAndStopped() throws MalformedObjectNameException, IOException {

        try {
            CommonProxyUtilities commonProxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            TestUtils.ensureRequiredInputFilesExist(temporaryFolder);
            Dashboard.setCommonProxyUtilities(commonProxyUtilities);

            // Creating a spy proxy to verify the invoked methods
            DockerSeleniumRemoteProxy spyProxy = spy(proxy);

            // Start pulling thread
            spyProxy.startPolling();

            // Get a test session
            TestSession newSession = spyProxy.getNewSession(getCapabilitySupportedByDockerSelenium());
            newSession.setExternalKey(new ExternalSessionKey("DockerSeleniumRemoteProxy Test"));
            Assert.assertNotNull(newSession);

            // We start the session, in order to start recording
            WebDriverRequest webDriverRequest = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("POST");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.START_SESSION);
            spyProxy.afterCommand(newSession, webDriverRequest, response);

            // Assert video recording started
            String containerId = spyProxy.getContainerId();
            verify(spyProxy, times(1)).
                    videoRecording(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING);
            verify(spyProxy, times(1)).
                    processContainerAction(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING,
                            containerId);

            // We release the sessions, the node should be free
            webDriverRequest = mock(WebDriverRequest.class);
            response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("DELETE");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.STOP_SESSION);

            newSession.getSlot().doFinishRelease();
            spyProxy.afterCommand(newSession, webDriverRequest, response);
            spyProxy.afterSession(newSession);

            Assert.assertFalse(spyProxy.isBusy());
            verify(spyProxy, timeout(40000))
                    .videoRecording(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING);
            verify(spyProxy, timeout(40000))
                    .processContainerAction(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING,
                            containerId);
            verify(spyProxy, timeout(40000)).copyVideos(containerId);
        } finally {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
            new JMXHelper().unregister(objectName);
            Dashboard.restoreCommonProxyUtilities();
        }
    }

    @Test
    public void videoRecordingIsDisabled() throws MalformedObjectNameException, IOException {

        try {
            // Mocking the environment variable to return false for video recording enabled
            Environment environment = mock(Environment.class);
            when(environment.getEnvVariable(DockerSeleniumRemoteProxy.ZALENIUM_VIDEO_RECORDING_ENABLED))
                    .thenReturn("false");
            when(environment.getIntEnvVariable(DockerSeleniumRemoteProxy.ZALENIUM_MAX_TEST_SESSIONS, 1))
                    .thenReturn(1);

            // Creating a spy proxy to verify the invoked methods
            CommonProxyUtilities commonProxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            TestUtils.ensureRequiredInputFilesExist(temporaryFolder);
            Dashboard.setCommonProxyUtilities(commonProxyUtilities);
            DockerSeleniumRemoteProxy spyProxy = spy(proxy);
            DockerSeleniumRemoteProxy.setEnv(environment);
            DockerSeleniumRemoteProxy.readEnvVars();


            // Start pulling thread
            spyProxy.startPolling();

            // Get a test session
            TestSession newSession = spyProxy.getNewSession(getCapabilitySupportedByDockerSelenium());
            newSession.setExternalKey(new ExternalSessionKey("DockerSeleniumRemoteProxy Test"));
            Assert.assertNotNull(newSession);

            // We start the session, in order to start recording
            WebDriverRequest webDriverRequest = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("POST");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.START_SESSION);
            spyProxy.afterCommand(newSession, webDriverRequest, response);

            // Assert no video recording was started, videoRecording is invoked but processContainerAction should not
            verify(spyProxy, times(1))
                    .videoRecording(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING);
            verify(spyProxy, never())
                    .processContainerAction(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING, "");

            // We release the sessions, the node should be free
            webDriverRequest = mock(WebDriverRequest.class);
            response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("DELETE");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.STOP_SESSION);

            newSession.getSlot().doFinishRelease();
            spyProxy.afterCommand(newSession, webDriverRequest, response);
            spyProxy.afterSession(newSession);

            Assert.assertFalse(spyProxy.isBusy());
            // Now we assert that videoRecording was invoked but processContainerAction not, neither copyVideos
            verify(spyProxy, timeout(40000))
                    .videoRecording(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING);
            verify(spyProxy, never())
                    .processContainerAction(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING, "");
            verify(spyProxy, never()).copyVideos("");
        } finally {
            DockerSeleniumRemoteProxy.restoreEnvironment();
            Dashboard.restoreCommonProxyUtilities();
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
            new JMXHelper().unregister(objectName);
        }
    }

    @Test
    public void videoRecordingIsDisabledViaCapability() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("recordVideo", false);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);
      Assert.assertFalse(proxy.isVideoRecordingEnabled());
    }

    private Map<String, Object> getCapabilitySupportedByDockerSelenium() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        return requestedCapability;
    }
}

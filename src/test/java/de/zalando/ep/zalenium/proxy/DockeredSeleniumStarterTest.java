package de.zalando.ep.zalenium.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.container.DockerContainerClient;
import de.zalando.ep.zalenium.container.kubernetes.KubernetesContainerClient;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.KubernetesContainerMock;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.TimeZone;
import java.util.function.Supplier;

@SuppressWarnings("Duplicates")
@RunWith(value = Parameterized.class)
public class DockeredSeleniumStarterTest {

    private ContainerClient containerClient;
    private DockerContainerClient originalDockerContainerClient;
    private KubernetesContainerClient originalKubernetesContainerClient;
    private Supplier<Boolean> originalIsKubernetesValue;
    private Supplier<Boolean> currentIsKubernetesValue;

    public DockeredSeleniumStarterTest(ContainerClient containerClient, Supplier<Boolean> isKubernetes) {
        this.containerClient = containerClient;
        this.currentIsKubernetesValue = isKubernetes;
        this.originalDockerContainerClient = ContainerFactory.getDockerContainerClient();
        this.originalIsKubernetesValue = ContainerFactory.getIsKubernetes();
        this.originalKubernetesContainerClient = ContainerFactory.getKubernetesContainerClient();
    }

    @Parameterized.Parameters
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

        DockeredSeleniumStarter.setContainerClient(containerClient);
    }

    @After
    public void afterMethod() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
        new JMXHelper().unregister(objectName);
        DockeredSeleniumStarter.restoreEnvironment();
        ZaleniumConfiguration.restoreEnvironment();
        ContainerFactory.setDockerContainerClient(originalDockerContainerClient);
        ContainerFactory.setIsKubernetes(originalIsKubernetesValue);
        ContainerFactory.setKubernetesContainerClient(originalKubernetesContainerClient);
    }

    @AfterClass
    public static void tearDown() {
        DockeredSeleniumStarter.restoreContainerClient();
        DockeredSeleniumStarter.restoreEnvironment();
        ZaleniumConfiguration.restoreEnvironment();
    }

    /*
    @Test
    public void noContainerIsStartedWhenCapabilitiesAreNotSupported() {

        // Non supported desired capability for the test session
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        nonSupportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(timeZone, screenSize);
    }
    */

    /*
    @Test
    public void noContainerIsStartedWhenPlatformIsNotSupported() {
        // Non supported desired capability for the test session
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        nonSupportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WINDOWS);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(timeZone, screenSize);
    }
    */

    /*
    @Test
    public void containerIsStartedWhenChromeCapabilitiesAreSupported() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(timeZone, screenSize);
    }
    */

    /*
    @Test
    public void containerIsStartedWhenScreenResolutionIsProvided() {
        // Supported desired capability for the test session
        Dimension customScreenSize = new Dimension(1280, 760);
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.ANY);
        String screenResolution = String.format("%sx%s", customScreenSize.getWidth(), customScreenSize.getHeight());
        supportedCapability.put("screenResolution", screenResolution);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(timeZone, customScreenSize);
    }
    */

    /*
    @Test
    public void containerIsStartedWhenResolutionIsProvided() {
        // Supported desired capability for the test session
        Dimension customScreenSize = new Dimension(1300, 900);
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.ANY);
        String screenResolution = String.format("%sx%s", customScreenSize.getWidth(), customScreenSize.getHeight());
        supportedCapability.put("resolution", screenResolution);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(timeZone, customScreenSize);
    }
    */

    /*
    @Test
    public void containerIsStartedWhenCustomScreenResolutionIsProvided() {
        // Supported desired capability for the test session
        Dimension customScreenSize = new Dimension(1500, 1000);
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.ANY);
        String screenResolution = String.format("%sx%s", customScreenSize.getWidth(), customScreenSize.getHeight());
        supportedCapability.put("screen-resolution", screenResolution);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(timeZone, customScreenSize);
    }
    */

    /*
    @Test
    public void containerIsStartedWhenCustomTimeZoneIsProvided() {
        // Supported desired capability for the test session
        TimeZone timeZone = TimeZone.getTimeZone("America/Montreal");
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.ANY);
        supportedCapability.put("tz", timeZone.getID());
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(timeZone, screenSize);
    }
    */

    /*
    @Test
    public void containerIsStartedWhenNegativeResolutionIsProvidedUsingDefaults() {
        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.ANY);
        supportedCapability.put("resolution", "-1300x800");
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(timeZone, screenSize);
    }
    */

    /*
    @Test
    public void containerIsStartedWhenAnInvalidResolutionIsProvidedUsingDefaults() {
        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.ANY);
        supportedCapability.put("screenResolution", "notAValidScreenResolution");
        TestSession testSession = spyProxy.getNewSession(supportedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(timeZone, screenSize);
    }
    */

    /*
    @Test
    public void containerIsStartedWhenFirefoxCapabilitiesAreSupported() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).times(1)).startDockerSeleniumContainer(timeZone, screenSize);
    }
    */

    /*
    @Test
    public void noContainerIsStartedWhenBrowserCapabilityIsAbsent() {
        // Browser is absent
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(timeZone, screenSize);
        verify(spyProxy, never()).startDockerSeleniumContainer(timeZone, screenSize);
    }

    @Test
    public void noContainerIsStartedForAlreadyProcessedRequest() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        ProcessedCapabilities processedCapabilities = new ProcessedCapabilities(requestedCapability,
                System.identityHashCode(requestedCapability));
        DockerSeleniumStarterRemoteProxy.processedCapabilitiesList.add(processedCapabilities);
        TestSession testSession = spyProxy.getNewSession(requestedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, times(0)).startDockerSeleniumContainer(timeZone, screenSize);
    }

    @Test
    public void containerIsStartedForRequestProcessedMoreThan30Times() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        ProcessedCapabilities processedCapabilities = new ProcessedCapabilities(requestedCapability,
                System.identityHashCode(requestedCapability));
        processedCapabilities.setProcessedTimes(31);
        DockerSeleniumStarterRemoteProxy.processedCapabilitiesList.add(processedCapabilities);
        TestSession testSession = spyProxy.getNewSession(requestedCapability);
        Assert.assertNull(testSession);
        verify(spyProxy, timeout(1000).atLeastOnce()).startDockerSeleniumContainer(timeZone, screenSize);
    }
    */


    /*
        Tests checking the environment variables setup to have a given number of containers on startup
     */

    @Test
    public void fallbackToDefaultAmountOfValuesWhenVariablesAreNotSet() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class, withSettings().useConstructor());
        when(environment.getEnvVariable(any(String.class))).thenReturn(null);
        when(environment.getIntEnvVariable(any(String.class), any(Integer.class))).thenCallRealMethod();
        when(environment.getStringEnvVariable(any(String.class), any(String.class))).thenCallRealMethod();
        DockeredSeleniumStarter.setEnv(environment);
        DockeredSeleniumStarter.readConfigurationFromEnvVariables();
        ZaleniumConfiguration.setEnv(environment);
        ZaleniumConfiguration.readConfigurationFromEnvVariables();

        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_AMOUNT_DESIRED_CONTAINERS,
                ZaleniumConfiguration.getDesiredContainersOnStartup());
        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
                ZaleniumConfiguration.getMaxDockerSeleniumContainers());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SCREEN_SIZE.getHeight(),
                DockeredSeleniumStarter.getConfiguredScreenSize().getHeight());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SCREEN_SIZE.getWidth(),
            DockeredSeleniumStarter.getConfiguredScreenSize().getWidth());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_TZ.getID(),
            DockeredSeleniumStarter.getConfiguredTimeZone().getID());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SELENIUM_NODE_PARAMS,
            DockeredSeleniumStarter.getSeleniumNodeParameters());
    }

    @Test
    public void fallbackToDefaultAmountValuesWhenVariablesAreNotIntegers() {

        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class, withSettings().useConstructor());
        when(environment.getEnvVariable(ZaleniumConfiguration.ZALENIUM_DESIRED_CONTAINERS))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(ZaleniumConfiguration.ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockeredSeleniumStarter.ZALENIUM_SCREEN_HEIGHT))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockeredSeleniumStarter.ZALENIUM_SCREEN_WIDTH))
                .thenReturn("ABC_NON_INTEGER");
        when(environment.getEnvVariable(DockeredSeleniumStarter.ZALENIUM_TZ))
                .thenReturn("ABC_NON_STANDARD_TIME_ZONE");
        when(environment.getIntEnvVariable(any(String.class), any(Integer.class))).thenCallRealMethod();
        when(environment.getStringEnvVariable(any(String.class), any(String.class))).thenCallRealMethod();
        DockeredSeleniumStarter.setEnv(environment);
        DockeredSeleniumStarter.readConfigurationFromEnvVariables();
        ZaleniumConfiguration.setEnv(environment);
        ZaleniumConfiguration.readConfigurationFromEnvVariables();

        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_AMOUNT_DESIRED_CONTAINERS,
            ZaleniumConfiguration.getDesiredContainersOnStartup());
        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
            ZaleniumConfiguration.getMaxDockerSeleniumContainers());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SCREEN_SIZE.getHeight(),
            DockeredSeleniumStarter.getConfiguredScreenSize().getHeight());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SCREEN_SIZE.getWidth(),
            DockeredSeleniumStarter.getConfiguredScreenSize().getWidth());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_TZ.getID(),
            DockeredSeleniumStarter.getConfiguredTimeZone().getID());
    }

    @Test
    public void variablesGrabTheConfiguredEnvironmentVariables() {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class, withSettings().useConstructor());
        int amountOfDesiredContainers = 7;
        int amountOfMaxContainers = 8;
        int screenWidth = 1440;
        int screenHeight = 810;
        String seleniumNodeParams = "-debug";
        TimeZone timeZone = TimeZone.getTimeZone("America/Montreal");
        when(environment.getEnvVariable(ZaleniumConfiguration.ZALENIUM_DESIRED_CONTAINERS))
                .thenReturn(String.valueOf(amountOfDesiredContainers));
        when(environment.getEnvVariable(ZaleniumConfiguration.ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS))
                .thenReturn(String.valueOf(amountOfMaxContainers));
        when(environment.getEnvVariable(DockeredSeleniumStarter.ZALENIUM_SCREEN_HEIGHT))
                .thenReturn(String.valueOf(screenHeight));
        when(environment.getEnvVariable(DockeredSeleniumStarter.ZALENIUM_SCREEN_WIDTH))
                .thenReturn(String.valueOf(screenWidth));
        when(environment.getEnvVariable(DockeredSeleniumStarter.ZALENIUM_TZ))
                .thenReturn(timeZone.getID());
        when(environment.getEnvVariable(DockeredSeleniumStarter.SELENIUM_NODE_PARAMS))
                .thenReturn(seleniumNodeParams);
        when(environment.getIntEnvVariable(any(String.class), any(Integer.class))).thenCallRealMethod();
        when(environment.getStringEnvVariable(any(String.class), any(String.class))).thenCallRealMethod();
        DockeredSeleniumStarter.setEnv(environment);
        DockeredSeleniumStarter.readConfigurationFromEnvVariables();
        ZaleniumConfiguration.setEnv(environment);
        ZaleniumConfiguration.readConfigurationFromEnvVariables();

        Assert.assertEquals(amountOfDesiredContainers, ZaleniumConfiguration.getDesiredContainersOnStartup());
        Assert.assertEquals(amountOfMaxContainers, ZaleniumConfiguration.getMaxDockerSeleniumContainers());
        Assert.assertEquals(screenHeight, DockeredSeleniumStarter.getConfiguredScreenSize().getHeight());
        Assert.assertEquals(screenWidth, DockeredSeleniumStarter.getConfiguredScreenSize().getWidth());
        Assert.assertEquals(timeZone.getID(), DockeredSeleniumStarter.getConfiguredTimeZone().getID());
        Assert.assertEquals(seleniumNodeParams, DockeredSeleniumStarter.getSeleniumNodeParameters());
    }
    /*

    @Test
    public void amountOfCreatedContainersIsTheConfiguredOne() throws MalformedObjectNameException {
        // Mock the environment class that serves as proxy to retrieve env variables
        Environment environment = mock(Environment.class, withSettings().useConstructor());
        int amountOfDesiredContainers = 7;
        when(environment.getEnvVariable(DockerSeleniumStarterRemoteProxy.ZALENIUM_DESIRED_CONTAINERS))
                .thenReturn(String.valueOf(amountOfDesiredContainers));
        when(environment.getIntEnvVariable(any(String.class), any(Integer.class))).thenCallRealMethod();
        DockerSeleniumStarterRemoteProxy.setEnv(environment);
        DockerSeleniumStarterRemoteProxy.setSleepIntervalMultiplier(0);

        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
            new JMXHelper().unregister(objectName);
        } finally {
            DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);
        }
        registry.add(spyProxy);

        verify(spyProxy, timeout(5000).times(amountOfDesiredContainers))
                .startDockerSeleniumContainer(timeZone, screenSize, true);
        Assert.assertEquals(amountOfDesiredContainers, DockerSeleniumStarterRemoteProxy.getDesiredContainersOnStartup());
    }
    */

    @Test
    public void noNegativeValuesAreAllowedForStartup() {
        ZaleniumConfiguration.setDesiredContainersOnStartup(-1);
        ZaleniumConfiguration.setMaxDockerSeleniumContainers(-1);
        ZaleniumConfiguration.setTimeToWaitToStart(-10);
        ZaleniumConfiguration.setWaitForAvailableNodes(true);
        ZaleniumConfiguration.setMaxTimesToProcessRequest(-10);
        DockeredSeleniumStarter.setConfiguredScreenSize(new Dimension(-1, -1));
        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_AMOUNT_DESIRED_CONTAINERS,
                ZaleniumConfiguration.getDesiredContainersOnStartup());
        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING,
            ZaleniumConfiguration.getMaxDockerSeleniumContainers());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SCREEN_SIZE.getWidth(),
            DockeredSeleniumStarter.getConfiguredScreenSize().getWidth());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SCREEN_SIZE.getHeight(),
            DockeredSeleniumStarter.getConfiguredScreenSize().getHeight());
        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_TIME_TO_WAIT_TO_START,
            ZaleniumConfiguration.getTimeToWaitToStart());
        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_TIMES_TO_PROCESS_REQUEST,
            ZaleniumConfiguration.getMaxTimesToProcessRequest());
        Assert.assertTrue(ZaleniumConfiguration.isWaitForAvailableNodes());
    }

}

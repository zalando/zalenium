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
    private Supplier<DockerContainerClient> originalDockerContainerClient;
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
            ContainerFactory.setDockerContainerClient(() -> (DockerContainerClient) containerClient);
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
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SELENIUM_NODE_HOST,
                DockeredSeleniumStarter.getSeleniumNodeHost());
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
        int browserTimeout = 1000;
        String seleniumNodeParams = "-debug";
        String seleniumNodeHost = "0.0.0.0";
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
        when(environment.getEnvVariable(DockeredSeleniumStarter.SELENIUM_NODE_HOST))
                .thenReturn(seleniumNodeHost);
        when(environment.getEnvVariable("SEL_BROWSER_TIMEOUT_SECS"))
                .thenReturn(String.valueOf(browserTimeout));
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
        Assert.assertEquals(seleniumNodeHost, DockeredSeleniumStarter.getSeleniumNodeHost());
        Assert.assertEquals(browserTimeout, DockeredSeleniumStarter.getBrowserTimeout());
    }

    @Test
    public void noNegativeValuesAreAllowedForStartup() {
        ZaleniumConfiguration.setDesiredContainersOnStartup(-1);
        ZaleniumConfiguration.setMaxDockerSeleniumContainers(-1);
        ZaleniumConfiguration.setTimeToWaitToStart(-10);
        ZaleniumConfiguration.setWaitForAvailableNodes(true);
        ZaleniumConfiguration.setMaxTimesToProcessRequest(-10);
        ZaleniumConfiguration.setCheckContainersInterval(500);
        DockeredSeleniumStarter.setBrowserTimeout(-100);
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
        Assert.assertEquals(ZaleniumConfiguration.DEFAULT_CHECK_CONTAINERS_INTERVAL,
                ZaleniumConfiguration.getCheckContainersInterval());
        Assert.assertTrue(ZaleniumConfiguration.isWaitForAvailableNodes());
        Assert.assertEquals(DockeredSeleniumStarter.DEFAULT_SEL_BROWSER_TIMEOUT_SECS,
                DockeredSeleniumStarter.getBrowserTimeout());
    }

}

package de.zalando.ep.zalenium.proxy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import org.openqa.selenium.Dimension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;

/**
 * The idea of this proxy instance is:
 * 1. Receive a session request with some requested capabilities
 * 2. Start a docker-selenium container that will register with the hub
 * 3. Reject the received request
 * 4. When the registry receives the rejected request and sees the new registered node from step 2,
 * the process will flow as normal.
 */

public class DockerSeleniumProxyConfiguration {

    public static final int NO_VNC_PORT_GAP = 10000;
    @VisibleForTesting
    static final int DEFAULT_AMOUNT_DESIRED_CONTAINERS = 0;
    @VisibleForTesting
    static final int DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING = 10;
    @VisibleForTesting
    static final TimeZone DEFAULT_TZ = TimeZone.getTimeZone("Europe/Berlin");
    @VisibleForTesting
    static final Dimension DEFAULT_SCREEN_SIZE = new Dimension(1920, 1080);
    @VisibleForTesting
    static final String ZALENIUM_DESIRED_CONTAINERS = "ZALENIUM_DESIRED_CONTAINERS";
    @VisibleForTesting
    static final String ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS = "ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS";
    @VisibleForTesting
    static final String ZALENIUM_TZ = "ZALENIUM_TZ";
    @VisibleForTesting
    static final String ZALENIUM_SCREEN_WIDTH = "ZALENIUM_SCREEN_WIDTH";
    @VisibleForTesting
    static final String ZALENIUM_SCREEN_HEIGHT = "ZALENIUM_SCREEN_HEIGHT";
    @VisibleForTesting
    static final String SELENIUM_NODE_PARAMS = "ZALENIUM_NODE_PARAMS";
    @VisibleForTesting
    static final String DEFAULT_SELENIUM_NODE_PARAMS = "";
    private static final String DEFAULT_ZALENIUM_CONTAINER_NAME = "zalenium";
    private static final String ZALENIUM_CONTAINER_NAME = "ZALENIUM_CONTAINER_NAME";
    private static final int LOWER_PORT_BOUNDARY = 40000;
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerSeleniumProxyConfiguration.class.getName());
    private static final String DEFAULT_DOCKER_SELENIUM_IMAGE = "elgalu/selenium";
    private static final String ZALENIUM_SELENIUM_IMAGE_NAME = "ZALENIUM_SELENIUM_IMAGE_NAME";
    private static final Environment defaultEnvironment = new Environment();
    
    @VisibleForTesting
    private static Environment env = defaultEnvironment;
    private static GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private static String seleniumNodeParameters = DEFAULT_SELENIUM_NODE_PARAMS;
    private static int desiredContainersOnStartup;
    private static int maxDockerSeleniumContainers;
    private static boolean seleniumWaitForContainer = true;
    private static boolean sendAnonymousUsageInfo = false;
    private static boolean waitForAvailableNodes = true;
    private static String browserTimeout = "16000";
    private static TimeZone configuredTimeZone;
    private static Dimension configuredScreenSize;
    private static String containerName;
    private static String dockerSeleniumImageName;

    /*
     * Reading configuration values from the env variables, if a value was not provided it falls back to defaults.
     */
    private static void readConfigurationFromEnvVariables() {

        int desiredContainers = env.getIntEnvVariable(ZALENIUM_DESIRED_CONTAINERS, DEFAULT_AMOUNT_DESIRED_CONTAINERS);
        setDesiredContainersOnStartup(desiredContainers);

        int maxDSContainers = env.getIntEnvVariable(ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS,
                DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING);
        setMaxDockerSeleniumContainers(maxDSContainers);

        int sWidth = env.getIntEnvVariable(ZALENIUM_SCREEN_WIDTH, DEFAULT_SCREEN_SIZE.getWidth());
        int sHeight = env.getIntEnvVariable(ZALENIUM_SCREEN_HEIGHT, DEFAULT_SCREEN_SIZE.getHeight());
        setConfiguredScreenSize(new Dimension(sWidth, sHeight));

        String tz = env.getStringEnvVariable(ZALENIUM_TZ, DEFAULT_TZ.getID());
        setConfiguredTimeZone(tz);

        String containerN = env.getStringEnvVariable(ZALENIUM_CONTAINER_NAME, DEFAULT_ZALENIUM_CONTAINER_NAME);
        setContainerName(containerN);

        String seleniumImageName = env.getStringEnvVariable(ZALENIUM_SELENIUM_IMAGE_NAME, DEFAULT_DOCKER_SELENIUM_IMAGE);
        setDockerSeleniumImageName(seleniumImageName);

        String seleniumNodeParams = env.getStringEnvVariable(SELENIUM_NODE_PARAMS, DEFAULT_SELENIUM_NODE_PARAMS);
        setSeleniumNodeParameters(seleniumNodeParams);

        seleniumWaitForContainer = env.getBooleanEnvVariable("SELENIUM_WAIT_FOR_CONTAINER", true);

        sendAnonymousUsageInfo = env.getBooleanEnvVariable("ZALENIUM_SEND_ANONYMOUS_USAGE_INFO", false);

        waitForAvailableNodes = env.getBooleanEnvVariable("WAIT_FOR_AVAILABLE_NODES", true);

        browserTimeout = env.getStringEnvVariable("SEL_BROWSER_TIMEOUT_SECS", "16000");
    }
    
    static {
    	readConfigurationFromEnvVariables();
    }
    
    private Map<String, String> buildEnvVars(TimeZone timeZone, Dimension screenSize, String hostIpAddress,
            boolean sendAnonymousUsageInfo, String nodePolling,
            String nodeRegisterCycle, String seleniumNodeParams) {
            final int noVncPort = LOWER_PORT_BOUNDARY + NO_VNC_PORT_GAP;
            final int vncPort = LOWER_PORT_BOUNDARY + VNC_PORT_GAP;
            Map<String, String> envVars = new HashMap<>();
            envVars.put("ZALENIUM", "true");
            envVars.put("SELENIUM_HUB_HOST", hostIpAddress);
            envVars.put("SELENIUM_HUB_PORT", "4445");
            envVars.put("SELENIUM_NODE_HOST", "{{CONTAINER_IP}}");
            envVars.put("GRID", "false");
            envVars.put("WAIT_TIMEOUT", "120s");
            envVars.put("PICK_ALL_RANDOM_PORTS", "false");
            envVars.put("VIDEO_STOP_SLEEP_SECS", "1");
            envVars.put("WAIT_TIME_OUT_VIDEO_STOP", "20s");
            envVars.put("SEND_ANONYMOUS_USAGE_INFO", String.valueOf(sendAnonymousUsageInfo));
            envVars.put("BUILD_URL", env.getStringEnvVariable("BUILD_URL", ""));
            envVars.put("NOVNC", "true");
            envVars.put("NOVNC_PORT", String.valueOf(noVncPort));
            envVars.put("VNC_PORT", String.valueOf(vncPort));
            envVars.put("SCREEN_WIDTH", String.valueOf(screenSize.getWidth()));
            envVars.put("SCREEN_HEIGHT", String.valueOf(screenSize.getHeight()));
            envVars.put("TZ", timeZone.getID());
            envVars.put("SELENIUM_NODE_REGISTER_CYCLE", nodeRegisterCycle);
            envVars.put("SEL_NODEPOLLING_MS", nodePolling);
            envVars.put("SELENIUM_NODE_PROXY_PARAMS", "de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy");
            envVars.put("MULTINODE", "true");
            envVars.put("SELENIUM_MULTINODE_PORT", String.valueOf(LOWER_PORT_BOUNDARY));
            envVars.put("CHROME", "false");
            envVars.put("FIREFOX", "false");
            envVars.put("SELENIUM_NODE_PARAMS", seleniumNodeParams);
            return envVars;
    }
 
    public static String getContainerName() {
        return Optional.ofNullable(containerName).orElse(DEFAULT_ZALENIUM_CONTAINER_NAME);
    }
    
    private static void setContainerName(String containerName) {
        DockerSeleniumProxyConfiguration.containerName = containerName;
    }
    
    public static String getDockerSeleniumImageName() {
        return Optional.ofNullable(dockerSeleniumImageName).orElse(DEFAULT_DOCKER_SELENIUM_IMAGE);
    }

    public static void setDockerSeleniumImageName(String dockerSeleniumImageName) {
        DockerSeleniumProxyConfiguration.dockerSeleniumImageName = dockerSeleniumImageName;
    }

    public static String getSeleniumNodeParameters() {
        return seleniumNodeParameters;
    }

    public static void setSeleniumNodeParameters(String seleniumNodeParameters) {
        DockerSeleniumProxyConfiguration.seleniumNodeParameters = seleniumNodeParameters;
    }

    @VisibleForTesting
    protected static int getDesiredContainersOnStartup() {
        return desiredContainersOnStartup;
    }

    @VisibleForTesting
    protected static void setDesiredContainersOnStartup(int desiredContainersOnStartup) {
        DockerSeleniumProxyConfiguration.desiredContainersOnStartup = desiredContainersOnStartup < 0 ?
                DEFAULT_AMOUNT_DESIRED_CONTAINERS : desiredContainersOnStartup;
    }

    @VisibleForTesting
    protected static int getMaxDockerSeleniumContainers() {
        return maxDockerSeleniumContainers;
    }

    @VisibleForTesting
    protected static void setMaxDockerSeleniumContainers(int maxDockerSeleniumContainers) {
        DockerSeleniumProxyConfiguration.maxDockerSeleniumContainers = maxDockerSeleniumContainers < 0 ?
                DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING : maxDockerSeleniumContainers;
    }

    public static Dimension getConfiguredScreenSize() {
        if (configuredScreenSize == null ||
                configuredScreenSize.getWidth() <= 0 || configuredScreenSize.getHeight() <= 0) {
            return DEFAULT_SCREEN_SIZE;
        }
        return DockerSeleniumProxyConfiguration.configuredScreenSize;
    }

    public static void setConfiguredScreenSize(Dimension configuredScreenSize) {
        if (configuredScreenSize.getWidth() <= 0 || configuredScreenSize.getHeight() <= 0) {
            DockerSeleniumProxyConfiguration.configuredScreenSize = DEFAULT_SCREEN_SIZE;
        } else {
            DockerSeleniumProxyConfiguration.configuredScreenSize = configuredScreenSize;
        }
    }

    public static TimeZone getConfiguredTimeZone() {
        return Optional.ofNullable(configuredTimeZone).orElse(DEFAULT_TZ);
    }

    public static void setConfiguredTimeZone(String configuredTimeZone) {
        if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(configuredTimeZone)) {
            LOGGER.warn(String.format("%s is not a real time zone.", configuredTimeZone));
            DockerSeleniumProxyConfiguration.configuredTimeZone = DEFAULT_TZ;
        } else {
            DockerSeleniumProxyConfiguration.configuredTimeZone = TimeZone.getTimeZone(configuredTimeZone);
        }
    }

}

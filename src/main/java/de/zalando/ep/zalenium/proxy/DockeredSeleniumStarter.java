package de.zalando.ep.zalenium.proxy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import de.zalando.ep.zalenium.container.DockerContainerClient;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import com.google.common.annotations.VisibleForTesting;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import de.zalando.ep.zalenium.util.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static de.zalando.ep.zalenium.util.ZaleniumConfiguration.ZALENIUM_RUNNING_LOCALLY;

public class DockeredSeleniumStarter {

    public static final int NO_VNC_PORT_GAP = 10000;
    private static final int VNC_PORT_GAP = 20000;

    @VisibleForTesting
    static final TimeZone DEFAULT_TZ = TimeZone.getTimeZone("Europe/Berlin");
    @VisibleForTesting
    static final Dimension DEFAULT_SCREEN_SIZE = new Dimension(1920, 1080);
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
    private static final Logger LOGGER = LoggerFactory.getLogger(DockeredSeleniumStarter.class.getName());
    private static final String DEFAULT_DOCKER_SELENIUM_IMAGE = "elgalu/selenium";
    private static final String ZALENIUM_SELENIUM_IMAGE_NAME = "ZALENIUM_SELENIUM_IMAGE_NAME";
    private static final int LOWER_PORT_BOUNDARY = 40000;
    private static final int UPPER_PORT_BOUNDARY = 49999;
    private static final ContainerClient defaultContainerClient = ContainerFactory.getContainerClient();
    private static final Environment defaultEnvironment = new Environment();
    private static final List<Integer> allocatedPorts = Collections.synchronizedList(new ArrayList<>());

    private static List<MutableCapabilities> dockerSeleniumCapabilities = new ArrayList<>();
    private static ContainerClient containerClient = defaultContainerClient;
    private static Environment env = defaultEnvironment;
    private static String latestDownloadedImage = null;
    private static String seleniumNodeParameters = DEFAULT_SELENIUM_NODE_PARAMS;
    private static boolean sendAnonymousUsageInfo = false;
    private static TimeZone configuredTimeZone;
    private static Dimension configuredScreenSize;
    private static String containerName;
    private static String dockerSeleniumImageName;
    private static Map<String, String> zaleniumProxyVars = new HashMap<>();

    private static final String[] HTTP_PROXY_ENV_VARS = {
            "zalenium_http_proxy",
            "zalenium_https_proxy",
            "zalenium_no_proxy"
    };
    
    /*
     * Reading configuration values from the env variables, if a value was not provided it falls back to defaults.
     */
    @VisibleForTesting
    public static void readConfigurationFromEnvVariables() {

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

        sendAnonymousUsageInfo = env.getBooleanEnvVariable("ZALENIUM_SEND_ANONYMOUS_USAGE_INFO", false);
        
        addProxyVars();
    }
    
    private static void addProxyVars() {
        Arrays.asList(HTTP_PROXY_ENV_VARS).forEach(httpEnvVar -> {
            String proxyValue = env.getStringEnvVariable(httpEnvVar, null);
            String httpEnvVarToAdd = httpEnvVar.replace("zalenium_", "");
            if (proxyValue != null) {
                zaleniumProxyVars.put(httpEnvVarToAdd, proxyValue);
            }
        });
    }
    
    static {
    	readConfigurationFromEnvVariables();
    }

    @SuppressWarnings("ConstantConditions")
    @VisibleForTesting
    public static List<MutableCapabilities> getCapabilities() {
        if (!dockerSeleniumCapabilities.isEmpty()) {
            return dockerSeleniumCapabilities;
        }

        dockerSeleniumCapabilities.clear();

        List<MutableCapabilities> dsCapabilities = new ArrayList<>();
        MutableCapabilities firefoxCapabilities = new MutableCapabilities();
        firefoxCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        firefoxCapabilities.setCapability(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        firefoxCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        dsCapabilities.add(firefoxCapabilities);
        MutableCapabilities chromeCapabilities = new MutableCapabilities();
        chromeCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        chromeCapabilities.setCapability(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        chromeCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        dsCapabilities.add(chromeCapabilities);

        dockerSeleniumCapabilities = dsCapabilities;
        return dockerSeleniumCapabilities;
    }

    @VisibleForTesting
    static void setContainerClient(final ContainerClient client) {
        containerClient = client;
    }

    @VisibleForTesting
    static void restoreContainerClient() {
        containerClient = defaultContainerClient;
    }

    public static String getContainerName() {
        return Optional.ofNullable(containerName).orElse(DEFAULT_ZALENIUM_CONTAINER_NAME);
    }
    
    private static void setContainerName(String containerName) {
        DockeredSeleniumStarter.containerName = containerName;
    }
    
    public static String getDockerSeleniumImageName() {
        return Optional.ofNullable(dockerSeleniumImageName).orElse(DEFAULT_DOCKER_SELENIUM_IMAGE);
    }

    public static void setDockerSeleniumImageName(String dockerSeleniumImageName) {
        DockeredSeleniumStarter.dockerSeleniumImageName = dockerSeleniumImageName;
    }

    public static String getSeleniumNodeParameters() {
        return seleniumNodeParameters;
    }

    public static void setSeleniumNodeParameters(String seleniumNodeParameters) {
        DockeredSeleniumStarter.seleniumNodeParameters = seleniumNodeParameters;
    }

    private static String getLatestDownloadedImage(String dockerSeleniumImageName) {
        if (latestDownloadedImage == null) {
            latestDownloadedImage = containerClient.getLatestDownloadedImage(dockerSeleniumImageName);
        }
        return latestDownloadedImage;
    }

    public static Dimension getConfiguredScreenSize() {
        if (configuredScreenSize == null ||
                configuredScreenSize.getWidth() <= 0 || configuredScreenSize.getHeight() <= 0) {
            return DEFAULT_SCREEN_SIZE;
        }
        return DockeredSeleniumStarter.configuredScreenSize;
    }

    public static void setConfiguredScreenSize(Dimension configuredScreenSize) {
        if (configuredScreenSize.getWidth() <= 0 || configuredScreenSize.getHeight() <= 0) {
            DockeredSeleniumStarter.configuredScreenSize = DEFAULT_SCREEN_SIZE;
        } else {
            DockeredSeleniumStarter.configuredScreenSize = configuredScreenSize;
        }
    }

    public static TimeZone getConfiguredTimeZone() {
        return Optional.ofNullable(configuredTimeZone).orElse(DEFAULT_TZ);
    }

    public static void setConfiguredTimeZone(String configuredTimeZone) {
        if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(configuredTimeZone)) {
            LOGGER.warn(String.format("%s is not a real time zone.", configuredTimeZone));
            DockeredSeleniumStarter.configuredTimeZone = DEFAULT_TZ;
        } else {
            DockeredSeleniumStarter.configuredTimeZone = TimeZone.getTimeZone(configuredTimeZone);
        }
    }

    @VisibleForTesting
    protected static void setEnv(final Environment env) {
        DockeredSeleniumStarter.env = env;
    }

    @VisibleForTesting
    static void restoreEnvironment() {
        env = defaultEnvironment;
    }


    public ContainerCreationStatus startDockerSeleniumContainer(Map<String, Object> requestedCapability) {
        // Check and configure specific screen resolution capabilities when they have been passed in the test config.
        Dimension screenSize = getConfiguredScreenResolutionFromCapabilities(requestedCapability);

        // Check and configure time zone capabilities when they have been passed in the test config.
        TimeZone timeZone = getConfiguredTimeZoneFromCapabilities(requestedCapability);
        
        ContainerCreationStatus containerCreationStatus = startDockerSeleniumContainer(timeZone, screenSize);
        if (containerCreationStatus.isCreated()) {
            LOGGER.debug(String.format("Created container [%s] with dimensions [%s] and tz [%s].",
                containerCreationStatus.getContainerName(), screenSize, timeZone));
            return containerCreationStatus;        	
        }
        else {
        	LOGGER.warn("No container was created, will wait until request is processed again...");
        	return null;
        }
    }
    
    public ContainerCreationStatus startDockerSeleniumContainer(final TimeZone timeZone, final Dimension screenSize) {

        TimeZone effectiveTimeZone = ObjectUtils.defaultIfNull(timeZone, DEFAULT_TZ);
        Dimension effectiveScreenSize = ObjectUtils.defaultIfNull(screenSize, DEFAULT_SCREEN_SIZE);

        NetworkUtils networkUtils = new NetworkUtils();
        String hostIpAddress = networkUtils.getIp4NonLoopbackAddressOfThisMachine().getHostAddress();
        String nodePolling = String.valueOf(RandomUtils.nextInt(90, 120) * 1000);
        String nodeRegisterCycle = String.valueOf(RandomUtils.nextInt(15, 25) * 1000);
        String seleniumNodeParams = getSeleniumNodeParameters();
        String latestImage = getLatestDownloadedImage(getDockerSeleniumImageName());

        int containerPort = LOWER_PORT_BOUNDARY;
        if (containerClient instanceof DockerContainerClient) {
            containerPort = findFreePortInRange();
        }
        Map<String, String> envVars = buildEnvVars(effectiveTimeZone, effectiveScreenSize, hostIpAddress, sendAnonymousUsageInfo,
            nodePolling, nodeRegisterCycle, seleniumNodeParams, containerPort);

        return containerClient.createContainer(getContainerName(), latestImage, envVars, String.valueOf(containerPort));
    }

    private Map<String, String> buildEnvVars(TimeZone timeZone, Dimension screenSize, String hostIpAddress,
            boolean sendAnonymousUsageInfo, String nodePolling, String nodeRegisterCycle,
            String seleniumNodeParams, int containerPort) {
        final int noVncPort = containerPort + NO_VNC_PORT_GAP;
        final int vncPort = containerPort + VNC_PORT_GAP;
        Map<String, String> envVars = new HashMap<>();
        envVars.put("ZALENIUM", "true");
        envVars.put("SELENIUM_HUB_HOST", hostIpAddress);
        envVars.put("SELENIUM_HUB_PORT", "4445");
        envVars.put("SELENIUM_NODE_HOST", "0.0.0.0");
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
        envVars.put("SELENIUM_MULTINODE_PORT", String.valueOf(containerPort));
        envVars.put("CHROME", "false");
        envVars.put("FIREFOX", "false");
        if (ZALENIUM_RUNNING_LOCALLY) {
            envVars.put("SELENIUM_NODE_PARAMS", String.format("-remoteHost http://%s:%s", hostIpAddress, containerPort));
        } else {
            envVars.put("SELENIUM_NODE_PARAMS", seleniumNodeParams);
        }

        // Add the proxy vars
        envVars.putAll(zaleniumProxyVars);
        return envVars;
    }

    public boolean containerHasStarted(ContainerCreationStatus creationStatus) {
    	return containerClient.isReady(creationStatus);
    }

    public boolean containerHasFinished(ContainerCreationStatus creationStatus) {
    	return containerClient.isTerminated(creationStatus);
    }
    
    public void stopContainer(String containerId) {
    	containerClient.stopContainer(containerId);
    }

    /*
        This method will search for a screenResolution capability to be passed when creating a docker-selenium node.
    */
    private Dimension getConfiguredScreenResolutionFromCapabilities(Map<String, Object> requestedCapability) {
        Dimension screenSize = getConfiguredScreenSize();
        List<String> screenResolutionNames = Arrays.asList(ZaleniumCapabilityType.SCREEN_RESOLUTION,
                ZaleniumCapabilityType.SCREEN_RESOLUTION_NO_PREFIX, ZaleniumCapabilityType.RESOLUTION,
                ZaleniumCapabilityType.RESOLUTION_NO_PREFIX, ZaleniumCapabilityType.SCREEN_RESOLUTION_DASH,
                ZaleniumCapabilityType.SCREEN_RESOLUTION_DASH_NO_PREFIX);
        for (String screenResolutionName : screenResolutionNames) {
            if (requestedCapability.containsKey(screenResolutionName)) {
                String screenResolution = requestedCapability.get(screenResolutionName).toString();
                try {
                    int screenWidth = Integer.parseInt(screenResolution.split("x")[0]);
                    int screenHeight = Integer.parseInt(screenResolution.split("x")[1]);
                    if (screenWidth > 0 && screenHeight > 0) {
                        screenSize = new Dimension(screenWidth, screenHeight);
                    } else {
                        LOGGER.debug("One of the values provided for screenResolution is negative, " +
                                "defaults will be used. Passed value -> " + screenResolution);
                    }
                } catch (Exception e) {
                    LOGGER.debug("Values provided for screenResolution are not valid integers or " +
                            "either the width or the height is missing, defaults will be used. Passed value -> "
                            + screenResolution);
                }
            }
        }
        return screenSize;
    }

    /*
    This method will search for a tz capability to be passed when creating a docker-selenium node.
    */
    private TimeZone getConfiguredTimeZoneFromCapabilities(Map<String, Object> requestedCapability) {
        List<String> timeZoneCapabilities = Arrays.asList(ZaleniumCapabilityType.TIME_ZONE_NO_PREFIX,
                ZaleniumCapabilityType.TIME_ZONE);
        TimeZone timeZone = getConfiguredTimeZone();
        for (String timeZoneCapability : timeZoneCapabilities) {
            if (requestedCapability.containsKey(timeZoneCapability)) {
                String timeZoneFromCapabilities = requestedCapability.get(timeZoneCapability).toString();
                if (Arrays.asList(TimeZone.getAvailableIDs()).contains(timeZoneFromCapabilities)) {
                    timeZone = TimeZone.getTimeZone(timeZoneFromCapabilities);
                }
            }
        }

        return timeZone;
    }

    /*
            Method adapted from https://gist.github.com/vorburger/3429822
         */
    private int findFreePortInRange() {
        /*
            If the list size is this big (~9800), it means that almost all ports have been used, but
            probably many have been released already. The list is cleared so ports can be reused.
            If by any chance one of the first allocated ports is still used, it will be skipped by the
            existing validation.
         */
        synchronized (allocatedPorts){
            if (allocatedPorts.size() > (UPPER_PORT_BOUNDARY - LOWER_PORT_BOUNDARY - 200)) {
                allocatedPorts.clear();
                LOGGER.info("Cleaning allocated ports list.");
            }
            for (int portNumber = LOWER_PORT_BOUNDARY; portNumber <= UPPER_PORT_BOUNDARY; portNumber++) {
                int noVncPortNumber = portNumber + NO_VNC_PORT_GAP;
                int vncPortNumber = portNumber + VNC_PORT_GAP;
                if (!allocatedPorts.contains(portNumber) && !allocatedPorts.contains(noVncPortNumber)
                    && !allocatedPorts.contains(vncPortNumber)) {
                    allocatedPorts.add(portNumber);
                    allocatedPorts.add(noVncPortNumber);
                    allocatedPorts.add(vncPortNumber);
                    return portNumber;
                }
            }
        }
        return -1;
    }
}

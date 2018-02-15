package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.matcher.DockerSeleniumCapabilityMatcher;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import de.zalando.ep.zalenium.util.ProcessedCapabilities;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.listeners.RegistrationListener;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.beta.WebProxyHtmlRendererBeta;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.server.jmx.ManagedService;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The idea of this proxy instance is:
 * 1. Receive a session request with some requested capabilities
 * 2. Start a docker-selenium container that will register with the hub
 * 3. Reject the received request
 * 4. When the registry receives the rejected request and sees the new registered node from step 2,
 * the process will flow as normal.
 */

@SuppressWarnings("WeakerAccess")
@ManagedService(description = "DockerSeleniumStarter TestSlots")
public class DockerSeleniumStarterRemoteProxy extends DefaultRemoteProxy implements RegistrationListener {

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
    private static final Logger LOGGER = Logger.getLogger(DockerSeleniumStarterRemoteProxy.class.getName());
    private static final String DEFAULT_DOCKER_SELENIUM_IMAGE = "elgalu/selenium";
    private static final String ZALENIUM_SELENIUM_IMAGE_NAME = "ZALENIUM_SELENIUM_IMAGE_NAME";
    private static final int LOWER_PORT_BOUNDARY = 40000;
    private static final int UPPER_PORT_BOUNDARY = 49999;
    private static final int VNC_PORT_GAP = 20000;
    private static final ContainerClient defaultContainerClient = ContainerFactory.getContainerClient();
    private static final Environment defaultEnvironment = new Environment();
    private static final String LOGGING_PREFIX = "[DS] ";
    private static final List<Integer> allocatedPorts = Collections.synchronizedList(new ArrayList<>());
    @VisibleForTesting
    static List<ProcessedCapabilities> processedCapabilitiesList = new ArrayList<>();
    private static List<MutableCapabilities> dockerSeleniumCapabilities = new ArrayList<>();
    private static ContainerClient containerClient = defaultContainerClient;
    private static Environment env = defaultEnvironment;
    private static GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private static String latestDownloadedImage = null;
    private static String seleniumNodeParameters = DEFAULT_SELENIUM_NODE_PARAMS;
    private static int desiredContainersOnStartup;
    private static int maxDockerSeleniumContainers;
    private static int sleepIntervalMultiplier = 1000;
    private static boolean seleniumWaitForContainer = true;
    private static boolean sendAnonymousUsageInfo = false;
    private static TimeZone configuredTimeZone;
    private static Dimension configuredScreenSize;
    private static String containerName;
    private static String dockerSeleniumImageName;
    private static ThreadPoolExecutor poolExecutor;
    private final HtmlRenderer renderer = new WebProxyHtmlRendererBeta(this);
    private CapabilityMatcher capabilityHelper;

    @SuppressWarnings("WeakerAccess")
    public DockerSeleniumStarterRemoteProxy(RegistrationRequest request, GridRegistry registry) {
        super(updateDSCapabilities(request), registry);
    }

    /*
     * Reading configuration values from the env variables, if a value was not provided it falls back to defaults.
     */
    private static void readConfigurationFromEnvVariables() {

        int desiredContainers = env.getIntEnvVariable(ZALENIUM_DESIRED_CONTAINERS, DEFAULT_AMOUNT_DESIRED_CONTAINERS);
        setDesiredContainersOnStartup(desiredContainers);

        int maxDSContainers = env.getIntEnvVariable(ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS,
                DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING);
        setMaxDockerSeleniumContainers(maxDSContainers);
        poolExecutor = new ThreadPoolExecutor(maxDSContainers, maxDSContainers, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

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
    }

    /*
     *  Updating the proxy's registration request information with the current DockerSelenium capabilities.
     *  If it is not possible to retrieve them, then we default to Chrome and Firefox in Linux.
     */
    @VisibleForTesting
    protected static RegistrationRequest updateDSCapabilities(RegistrationRequest registrationRequest) {
        readConfigurationFromEnvVariables();
        containerClient.setNodeId(LOGGING_PREFIX);
        registrationRequest.getConfiguration().capabilities.clear();
        registrationRequest.getConfiguration().capabilities.addAll(getCapabilities());
        return registrationRequest;
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
        DockerSeleniumStarterRemoteProxy.containerName = containerName;
    }
    
    public static String getDockerSeleniumImageName() {
        return Optional.ofNullable(dockerSeleniumImageName).orElse(DEFAULT_DOCKER_SELENIUM_IMAGE);
    }

    public static void setDockerSeleniumImageName(String dockerSeleniumImageName) {
        DockerSeleniumStarterRemoteProxy.dockerSeleniumImageName = dockerSeleniumImageName;
    }

    public static String getSeleniumNodeParameters() {
        return seleniumNodeParameters;
    }

    public static void setSeleniumNodeParameters(String seleniumNodeParameters) {
        DockerSeleniumStarterRemoteProxy.seleniumNodeParameters = seleniumNodeParameters;
    }

    @VisibleForTesting
    public static void setSleepIntervalMultiplier(int sleepIntervalMultiplier) {
        DockerSeleniumStarterRemoteProxy.sleepIntervalMultiplier = sleepIntervalMultiplier;
    }

    @VisibleForTesting
    protected static int getDesiredContainersOnStartup() {
        return desiredContainersOnStartup;
    }

    @VisibleForTesting
    protected static void setDesiredContainersOnStartup(int desiredContainersOnStartup) {
        DockerSeleniumStarterRemoteProxy.desiredContainersOnStartup = desiredContainersOnStartup < 0 ?
                DEFAULT_AMOUNT_DESIRED_CONTAINERS : desiredContainersOnStartup;
    }

    @VisibleForTesting
    protected static int getMaxDockerSeleniumContainers() {
        return maxDockerSeleniumContainers;
    }

    @VisibleForTesting
    protected static void setMaxDockerSeleniumContainers(int maxDockerSeleniumContainers) {
        DockerSeleniumStarterRemoteProxy.maxDockerSeleniumContainers = maxDockerSeleniumContainers < 0 ?
                DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING : maxDockerSeleniumContainers;
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
        return DockerSeleniumStarterRemoteProxy.configuredScreenSize;
    }

    public static void setConfiguredScreenSize(Dimension configuredScreenSize) {
        if (configuredScreenSize.getWidth() <= 0 || configuredScreenSize.getHeight() <= 0) {
            DockerSeleniumStarterRemoteProxy.configuredScreenSize = DEFAULT_SCREEN_SIZE;
        } else {
            DockerSeleniumStarterRemoteProxy.configuredScreenSize = configuredScreenSize;
        }
    }

    public static TimeZone getConfiguredTimeZone() {
        return Optional.ofNullable(configuredTimeZone).orElse(DEFAULT_TZ);
    }

    public static void setConfiguredTimeZone(String configuredTimeZone) {
        if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(configuredTimeZone)) {
            LOGGER.log(Level.WARNING, () -> String.format("%s is not a real time zone.", configuredTimeZone));
            DockerSeleniumStarterRemoteProxy.configuredTimeZone = DEFAULT_TZ;
        } else {
            DockerSeleniumStarterRemoteProxy.configuredTimeZone = TimeZone.getTimeZone(configuredTimeZone);
        }
    }

    @VisibleForTesting
    protected static void setEnv(final Environment env) {
        DockerSeleniumStarterRemoteProxy.env = env;
    }

    @VisibleForTesting
    static void restoreEnvironment() {
        env = defaultEnvironment;
    }

    @Override
    public void teardown() {
        super.teardown();
        poolExecutor.shutdown();
    }

    public HtmlRenderer getHtmlRender() {
        return this.renderer;
    }

    /**
     * Receives a request to create a new session, but instead of accepting it, it will create a
     * docker-selenium container which will register to the hub, then reject the request and the hub
     * will assign the request to the new registered node.
     */
    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {

        if (!hasCapability(requestedCapability)) {
            LOGGER.log(Level.FINE, LOGGING_PREFIX + "Capability not supported {0}", requestedCapability);
            return null;
        }

        if (!requestedCapability.containsKey(CapabilityType.BROWSER_NAME)) {
            LOGGER.log(Level.INFO, () -> String.format("%s Capability %s does no contain %s key.", LOGGING_PREFIX,
                    requestedCapability, CapabilityType.BROWSER_NAME));
            return null;
        }

        // Check and configure specific screen resolution capabilities when they have been passed in the test config.
        Dimension screenSize = getConfiguredScreenResolutionFromCapabilities(requestedCapability);

        // Check and configure time zone capabilities when they have been passed in the test config.
        TimeZone timeZone = getConfiguredTimeZoneFromCapabilities(requestedCapability);

        /*
            Reusing nodes, rejecting requests when a node is cleaning up and will be ready again soon.
         */
        if (nodesAvailable(requestedCapability)) {
            LOGGER.log(Level.FINE, LOGGING_PREFIX + "A node is coming up soon for {0}, won't start a new node yet.",
                    requestedCapability);
            return null;
        }

        // Checking if this request has been processed based on its id, contents, and attempts
        if (hasRequestBeenProcessed(requestedCapability)) {
            LOGGER.log(Level.FINE, LOGGING_PREFIX + "Request {0}, has been processed and it is waiting for a node.",
                    requestedCapability);
            return null;
        }
        ProcessedCapabilities processedCapabilities = new ProcessedCapabilities(requestedCapability,
                System.identityHashCode(requestedCapability));
        processedCapabilitiesList.add(processedCapabilities);
        LOGGER.log(Level.INFO, LOGGING_PREFIX + "Starting new node for {0}.", requestedCapability);
        poolExecutor.execute(() -> startDockerSeleniumContainer(timeZone, screenSize));
        cleanProcessedCapabilities();

        return null;
    }

    private boolean hasRequestBeenProcessed(Map<String, Object> requestedCapability) {
        int requestedCapabilityHashCode = System.identityHashCode(requestedCapability);
        for (ProcessedCapabilities processedCapability : processedCapabilitiesList) {

            LOGGER.log(Level.FINE, LOGGING_PREFIX + "System.identityHashCode(requestedCapability) -> "
                    + System.identityHashCode(requestedCapability) + ", " + requestedCapability);
            LOGGER.log(Level.FINE, LOGGING_PREFIX + "processedCapability.getIdentityHashCode() -> "
                    + processedCapability.getIdentityHashCode() + ", " + processedCapability.getRequestedCapability());

            if (processedCapability.getIdentityHashCode() == requestedCapabilityHashCode) {

                processedCapability.setLastProcessedTime(System.currentTimeMillis());
                int processedTimes = processedCapability.getProcessedTimes() + 1;
                processedCapability.setProcessedTimes(processedTimes);

                if (processedTimes >= 30) {
                    processedCapability.setProcessedTimes(1);
                    LOGGER.log(Level.INFO, LOGGING_PREFIX + "Request has waited 30 attempts for a node, something " +
                            "went wrong with the previous attempts, creating a new node for {0}.", requestedCapability);
                    return false;
                }

                return true;
            }

        }
        return false;
    }

    private void cleanProcessedCapabilities() {
        /*
            Cleaning processed capabilities to reduce the risk of having two objects with the same
            identityHashCode after the garbage collector did its job.
            Not a silver bullet solution, but should be good enough.
         */
        List<ProcessedCapabilities> processedCapabilitiesToRemove = new ArrayList<>();
        for (ProcessedCapabilities processedCapability : processedCapabilitiesList) {
            long timeSinceLastProcess = System.currentTimeMillis() - processedCapability.getLastProcessedTime();
            long maximumLastProcessedTime = 1000 * 60;
            if (timeSinceLastProcess >= maximumLastProcessedTime) {
                processedCapabilitiesToRemove.add(processedCapability);
            }
        }
        processedCapabilitiesList.removeAll(processedCapabilitiesToRemove);
    }

    @Override
    public void beforeRegistration() {
        containerClient.initialiseContainerEnvironment();
        Dashboard.loadTestInformationFromFile();
        Dashboard.setShutDownHook();
        createContainersOnStartup();
    }

    @Override
    public CapabilityMatcher getCapabilityHelper() {
        if (capabilityHelper == null) {
            capabilityHelper = new DockerSeleniumCapabilityMatcher(this);
        }
        return capabilityHelper;
    }

    /*
        Making the node seem as heavily used, in order to get it listed after the 'docker-selenium' nodes.
        98% used.
     */
    @Override
    public float getResourceUsageInPercent() {
        return 98;
    }

    @VisibleForTesting
    public boolean startDockerSeleniumContainer(TimeZone timeZone, Dimension screenSize) {
        return startDockerSeleniumContainer(timeZone, screenSize, false);
    }

    @VisibleForTesting
    public boolean startDockerSeleniumContainer(TimeZone timeZone, Dimension screenSize, boolean forceCreation) {

        NetworkUtils networkUtils = new NetworkUtils();
        String hostIpAddress = networkUtils.getIp4NonLoopbackAddressOfThisMachine().getHostAddress();
        String nodePolling = String.valueOf(RandomUtils.nextInt(90, 120) * 1000);
        String nodeRegisterCycle = String.valueOf(RandomUtils.nextInt(15, 25) * 1000);
        String seleniumNodeParams = getSeleniumNodeParameters();
        String latestImage = getLatestDownloadedImage(getDockerSeleniumImageName());

        int attempts = 0;
        int maxAttempts = 2;
        while (attempts < maxAttempts) {
            attempts++;
            if (forceCreation || validateAmountOfDockerSeleniumContainers()) {

                final int nodePort = findFreePortInRange(LOWER_PORT_BOUNDARY, UPPER_PORT_BOUNDARY);

                Map<String, String> envVars = buildEnvVars(timeZone, screenSize, hostIpAddress, sendAnonymousUsageInfo,
                        nodePolling, nodeRegisterCycle, nodePort, seleniumNodeParams);

                ContainerCreationStatus creationStatus = containerClient
                        .createContainer(getContainerName(), latestImage, envVars, String.valueOf(nodePort));
                if (creationStatus.isCreated() && checkContainerStatus(creationStatus)) {
                    return true;
                } else {
                    LOGGER.log(Level.FINE, String.format("%sContainer creation failed, retrying...", LOGGING_PREFIX));
                }
            } else {
                LOGGER.log(Level.INFO, String.format("%sNo container was created, will try again in a moment...",
                        LOGGING_PREFIX));
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.FINE, "Exception while making a pause during container creation.", e);
                }
            }
        }
        LOGGER.log(Level.INFO, String.format("%sNo container was created after 3 attempts, will wait until request is " +
                        "processed again...", LOGGING_PREFIX));
        return false;
    }

    private boolean checkContainerStatus(ContainerCreationStatus creationStatus) {
        long sleepInterval = sleepIntervalMultiplier;
        // In some environments we won't wait for the container to be ready since we can't get the IP. E.g Rancher.
        if (!seleniumWaitForContainer) {
            return true;
        }
        if (ContainerFactory.getIsKubernetes().get()) {
            sleepInterval = sleepInterval * 3;
        }
        String createdContainerName = creationStatus.getContainerName();
        String containerIp = containerClient.getContainerIp(createdContainerName);
        for (int i = 1; i <= 60; i++) {
            try {
                Thread.sleep(sleepInterval);
                if (containerIp == null || containerIp.trim().isEmpty()) {
                    containerIp = containerClient.getContainerIp(createdContainerName);
                }
                URL statusUrl = new URL(String.format("http://%s:%s/wd/hub/status", containerIp, creationStatus.getNodePort()));
                try {
                    String status = IOUtils.toString(statusUrl, StandardCharsets.UTF_8);
                    String successMessage = "\"Node is running\"";
                    if (status.contains(successMessage)) {
                        LOGGER.log(Level.INFO, String.format("%sContainer %s is up after ~%s seconds...",
                                LOGGING_PREFIX, createdContainerName, i * (sleepInterval / 1000)));
                        return true;
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Error while getting node status, probably the node is still starting up...");
                }
            } catch (MalformedURLException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Malformed status url.", e);
            }
        }
        String message = String.format("%sContainer %s took longer than 60 seconds to be up and ready, this might be " +
                        "a signal that you have reached the hardware limits for the number of concurrent threads " +
                        "that you want to execute.", LOGGING_PREFIX, createdContainerName);
        LOGGER.log(Level.INFO, message);
        return false;
    }

    private Map<String, String> buildEnvVars(TimeZone timeZone, Dimension screenSize, String hostIpAddress,
                                             boolean sendAnonymousUsageInfo, String nodePolling,
                                             String nodeRegisterCycle, int nodePort, String seleniumNodeParams) {
        final int noVncPort = nodePort + NO_VNC_PORT_GAP;
        final int vncPort = nodePort + VNC_PORT_GAP;
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
        envVars.put("SELENIUM_MULTINODE_PORT", String.valueOf(nodePort));
        envVars.put("CHROME", "false");
        envVars.put("FIREFOX", "false");
        envVars.put("SELENIUM_NODE_PARAMS", seleniumNodeParams);
        return envVars;
    }

    private void createContainersOnStartup() {
        int containersToCreate = getDesiredContainersOnStartup() > getMaxDockerSeleniumContainers() ?
                getMaxDockerSeleniumContainers() : getDesiredContainersOnStartup();
        LOGGER.log(Level.INFO, String.format("%s Setting up %s nodes...", LOGGING_PREFIX, getDesiredContainersOnStartup()));
        // Thread.sleep() is to avoid having containers starting at the same time
        for (int i = 0; i < containersToCreate; i++) {
            new Thread(() -> {
                try {
                    Thread.sleep(RandomUtils.nextInt(1, (containersToCreate / 2) + 1) * sleepIntervalMultiplier);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.WARNING, getId() + " Error sleeping before starting a container", e);
                }
                startDockerSeleniumContainer(getConfiguredTimeZone(), getConfiguredScreenSize(), true);
            }).start();
        }
        LOGGER.log(Level.INFO, String.format("%s containers were created, it will take a bit more until all get registered.", containersToCreate));
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
                        LOGGER.log(Level.FINE, "One of the values provided for screenResolution is negative, " +
                                "defaults will be used. Passed value -> " + screenResolution);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Values provided for screenResolution are not valid integers or " +
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

    private boolean nodesAvailable(Map<String, Object> requestedCapability) {
        for (RemoteProxy remoteProxy : this.getRegistry().getAllProxies()) {
            if (remoteProxy instanceof DockerSeleniumRemoteProxy) {
                DockerSeleniumRemoteProxy proxy = (DockerSeleniumRemoteProxy) remoteProxy;
                // If a node is cleaning up it will be available soon
                // It is faster and more resource wise to wait for the node to be ready
                if (proxy.isCleaningUpBeforeNextSession() && proxy.hasCapability(requestedCapability)) {
                    LOGGER.log(Level.FINE, LOGGING_PREFIX + "A node is coming up to handle this request.");
                    return true;
                }
            }
        }
        LOGGER.log(Level.FINE, LOGGING_PREFIX + "No sessions available, a new node will be created.");
        return false;
    }

    private boolean validateAmountOfDockerSeleniumContainers() {
        try {
            int numberOfDockerSeleniumContainers = containerClient.getRunningContainers(getDockerSeleniumImageName());
            if (numberOfDockerSeleniumContainers >= getMaxDockerSeleniumContainers()) {
                LOGGER.log(Level.WARNING, LOGGING_PREFIX + "Max. number of docker-selenium containers has been reached, " +
                        "no more will be created until the number decreases below {0}.", getMaxDockerSeleniumContainers());
                return false;
            }
            LOGGER.log(Level.FINE, () -> String.format("%s %s docker-selenium containers running", LOGGING_PREFIX,
                    numberOfDockerSeleniumContainers));
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, LOGGING_PREFIX + e.toString(), e);
            ga.trackException(e);
        }
        return false;
    }

    /*
        Method adapted from https://gist.github.com/vorburger/3429822
     */
    private int findFreePortInRange(int lowerBoundary, int upperBoundary) {
        /*
            If the list size is this big (~9800), it means that almost all ports have been used, but
            probably many have been released already. The list is cleared so ports can be reused.
            If by any chance one of the first allocated ports is still used, it will be skipped by the
            existing validation.
         */
        synchronized (allocatedPorts){
            if (allocatedPorts.size() > (upperBoundary - lowerBoundary - 200)) {
                allocatedPorts.clear();
                LOGGER.log(Level.INFO, () -> LOGGING_PREFIX + "Cleaning allocated ports list.");
            }
            for (int portNumber = lowerBoundary; portNumber <= upperBoundary; portNumber++) {
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

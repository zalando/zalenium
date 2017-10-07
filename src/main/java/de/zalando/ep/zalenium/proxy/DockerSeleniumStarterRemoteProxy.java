package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.container.kubernetes.KubernetesContainerClient;
import de.zalando.ep.zalenium.matcher.DockerSeleniumCapabilityMatcher;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import de.zalando.ep.zalenium.util.ProcessedCapabilities;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.listeners.RegistrationListener;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.beta.WebProxyHtmlRendererBeta;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Platform;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

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
public class DockerSeleniumStarterRemoteProxy extends DefaultRemoteProxy implements RegistrationListener {

    public static final int NO_VNC_PORT_GAP = 10000;
    @VisibleForTesting
    static final int DEFAULT_AMOUNT_CHROME_CONTAINERS = 0;
    @VisibleForTesting
    static final int DEFAULT_AMOUNT_FIREFOX_CONTAINERS = 0;
    @VisibleForTesting
    static final int DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING = 10;
    @VisibleForTesting
    static final TimeZone DEFAULT_TZ = TimeZone.getTimeZone("Europe/Berlin");
    @VisibleForTesting
    static final Dimension DEFAULT_SCREEN_SIZE = new Dimension(1920, 1080);
    @VisibleForTesting
    static final String ZALENIUM_CHROME_CONTAINERS = "ZALENIUM_CHROME_CONTAINERS";
    @VisibleForTesting
    static final String ZALENIUM_FIREFOX_CONTAINERS = "ZALENIUM_FIREFOX_CONTAINERS";
    @VisibleForTesting
    static final String ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS = "ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS";
    @VisibleForTesting
    static final String ZALENIUM_TZ = "ZALENIUM_TZ";
    @VisibleForTesting
    static final String ZALENIUM_SCREEN_WIDTH = "ZALENIUM_SCREEN_WIDTH";
    @VisibleForTesting
    static final String ZALENIUM_SCREEN_HEIGHT = "ZALENIUM_SCREEN_HEIGHT";
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
    private static List<DesiredCapabilities> dockerSeleniumCapabilities = new ArrayList<>();
    private static ContainerClient containerClient = defaultContainerClient;
    private static Environment env = defaultEnvironment;
    private static GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private static String chromeVersion = null;
    private static String firefoxVersion = null;
    private static String latestDownloadedImage = null;
    private static int chromeContainersOnStartup;
    private static int firefoxContainersOnStartup;
    private static int maxDockerSeleniumContainers;
    private static int sleepIntervalMultiplier = 1000;
    private static TimeZone configuredTimeZone;
    private static Dimension configuredScreenSize;
    private static String containerName;
    private static String dockerSeleniumImageName;
    private static ThreadPoolExecutor poolExecutor;
    private final HtmlRenderer renderer = new WebProxyHtmlRendererBeta(this);
    private CapabilityMatcher capabilityHelper;

    @SuppressWarnings("WeakerAccess")
    public DockerSeleniumStarterRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateDSCapabilities(request), registry);
    }

    /*
     * Reading configuration values from the env variables, if a value was not provided it falls back to defaults.
     */
    private static void readConfigurationFromEnvVariables() {

        int chromeContainers = env.getIntEnvVariable(ZALENIUM_CHROME_CONTAINERS, DEFAULT_AMOUNT_CHROME_CONTAINERS);
        setChromeContainersOnStartup(chromeContainers);

        int firefoxContainers = env.getIntEnvVariable(ZALENIUM_FIREFOX_CONTAINERS, DEFAULT_AMOUNT_FIREFOX_CONTAINERS);
        setFirefoxContainersOnStartup(firefoxContainers);

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
    public static List<DesiredCapabilities> getCapabilities() {
        if (!dockerSeleniumCapabilities.isEmpty()) {
            return dockerSeleniumCapabilities;
        }

        // Getting versions from the current docker-selenium image
        if (firefoxVersion == null) {
            firefoxVersion = containerClient.getLabelValue(getLatestDownloadedImage(), "selenium_firefox_version");
        }
        if (chromeVersion == null) {
            chromeVersion = containerClient.getLabelValue(getLatestDownloadedImage(), "selenium_chrome_version");
        }

        dockerSeleniumCapabilities.clear();

        List<DesiredCapabilities> dsCapabilities = new ArrayList<>();
        DesiredCapabilities firefoxCapabilities = new DesiredCapabilities();
        firefoxCapabilities.setBrowserName(BrowserType.FIREFOX);
        firefoxCapabilities.setPlatform(Platform.LINUX);
        if (firefoxVersion != null && !firefoxVersion.isEmpty()) {
            firefoxCapabilities.setVersion(firefoxVersion);
        }
        firefoxCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        dsCapabilities.add(firefoxCapabilities);
        DesiredCapabilities chromeCapabilities = new DesiredCapabilities();
        chromeCapabilities.setBrowserName(BrowserType.CHROME);
        chromeCapabilities.setPlatform(Platform.LINUX);
        if (chromeVersion != null && !chromeVersion.isEmpty()) {
            chromeCapabilities.setVersion(chromeVersion);
        }
        chromeCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        dsCapabilities.add(chromeCapabilities);

        dockerSeleniumCapabilities = dsCapabilities;
        LOGGER.log(Level.INFO, LOGGING_PREFIX + "Capabilities grabbed from the docker-selenium image");
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

    @VisibleForTesting
    protected static int getFirefoxContainersOnStartup() {
        return firefoxContainersOnStartup;
    }

    @VisibleForTesting
    protected static void setFirefoxContainersOnStartup(int firefoxContainersOnStartup) {
        DockerSeleniumStarterRemoteProxy.firefoxContainersOnStartup = firefoxContainersOnStartup < 0 ?
                DEFAULT_AMOUNT_FIREFOX_CONTAINERS : firefoxContainersOnStartup;
    }

    public static String getContainerName() {
        return containerName == null ? DEFAULT_ZALENIUM_CONTAINER_NAME : containerName;
    }
    
    private static void setContainerName(String containerName) {
        DockerSeleniumStarterRemoteProxy.containerName = containerName;
    }
    
    public static String getDockerSeleniumImageName() {
        return dockerSeleniumImageName == null ? DEFAULT_DOCKER_SELENIUM_IMAGE : dockerSeleniumImageName;
    }

    public static void setDockerSeleniumImageName(String dockerSeleniumImageName) {
        DockerSeleniumStarterRemoteProxy.dockerSeleniumImageName = dockerSeleniumImageName;
    }

    @VisibleForTesting
    public static void setSleepIntervalMultiplier(int sleepIntervalMultiplier) {
        DockerSeleniumStarterRemoteProxy.sleepIntervalMultiplier = sleepIntervalMultiplier;
    }

    @VisibleForTesting
    protected static int getChromeContainersOnStartup() {
        return chromeContainersOnStartup;
    }

    @VisibleForTesting
    protected static void setChromeContainersOnStartup(int chromeContainersOnStartup) {
        DockerSeleniumStarterRemoteProxy.chromeContainersOnStartup = chromeContainersOnStartup < 0 ?
                DEFAULT_AMOUNT_CHROME_CONTAINERS : chromeContainersOnStartup;
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

    private static String getLatestDownloadedImage() {
        if (latestDownloadedImage == null) {
            latestDownloadedImage = containerClient.getLatestDownloadedImage(getDockerSeleniumImageName());
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
        return configuredTimeZone == null ? DEFAULT_TZ : configuredTimeZone;
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

        String browserName = requestedCapability.get(CapabilityType.BROWSER_NAME).toString();

        /*
            Reusing nodes, rejecting requests when test sessions are still available in the existing nodes.
         */
        if (testSessionsAvailable(requestedCapability)) {
            LOGGER.log(Level.FINE, LOGGING_PREFIX + "There are sessions available for {0}, won't start a new node yet.",
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
        poolExecutor.execute(() -> startDockerSeleniumContainer(browserName, timeZone, screenSize));
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

                /*
                // Leaving this code commented since it seems it is not needed anymore with the new processing logic
                // TODO: Check behaviour and see if it necessary to uncomment, otherwise just delete
                long pendingTasks = poolExecutor.getTaskCount() - poolExecutor.getCompletedTaskCount();
                if (pendingTasks == 0) {
                    LOGGER.log(Level.INFO, LOGGING_PREFIX + "No pending tasks, starting new node for {0}.", requestedCapability);
                    return false;
                }
                */

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
    public boolean startDockerSeleniumContainer(String browser, TimeZone timeZone, Dimension screenSize) {
        return startDockerSeleniumContainer(browser, timeZone, screenSize, false);
    }

    @VisibleForTesting
    public boolean startDockerSeleniumContainer(String browser, TimeZone timeZone, Dimension screenSize,
                                                boolean forceCreation) {

        if (forceCreation || validateAmountOfDockerSeleniumContainers()) {

            NetworkUtils networkUtils = new NetworkUtils();
            String hostIpAddress = networkUtils.getIp4NonLoopbackAddressOfThisMachine().getHostAddress();

            boolean sendAnonymousUsageInfo = env.getBooleanEnvVariable("ZALENIUM_SEND_ANONYMOUS_USAGE_INFO", false);
            String nodePolling = String.valueOf(RandomUtils.nextInt(90, 120) * 1000);

            int attempts = 0;
            int maxAttempts = 2;
            while (attempts < maxAttempts) {
                attempts++;
                final int nodePort = findFreePortInRange(LOWER_PORT_BOUNDARY, UPPER_PORT_BOUNDARY);

                Map<String, String> envVars = buildEnvVars(browser, timeZone, screenSize, hostIpAddress,
                        sendAnonymousUsageInfo, nodePolling, nodePort);

                String latestImage = containerClient.getLatestDownloadedImage(getDockerSeleniumImageName());
                ContainerCreationStatus creationStatus = containerClient
                        .createContainer(getContainerName(), latestImage, envVars, String.valueOf(nodePort));
                if (creationStatus.isCreated() && checkContainerStatus(creationStatus)) {
                    return true;
                } else {
                    LOGGER.log(Level.FINE, String.format("%sContainer creation failed, retrying...", LOGGING_PREFIX));
                }
            }
        }
        LOGGER.log(Level.INFO, String.format("%sNo container was created, will try again in a moment...",
                LOGGING_PREFIX));
        return false;
    }

    private boolean checkContainerStatus(ContainerCreationStatus creationStatus) {
        long sleepInterval = sleepIntervalMultiplier;
        if (containerClient instanceof KubernetesContainerClient) {
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
                                LOGGING_PREFIX, createdContainerName, i));
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

    private Map<String, String> buildEnvVars(String browser, TimeZone timeZone, Dimension screenSize,
                                             String hostIpAddress, boolean sendAnonymousUsageInfo, String nodePolling,
                                             int nodePort) {
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
        envVars.put("SELENIUM_NODE_REGISTER_CYCLE", "0");
        envVars.put("SEL_NODEPOLLING_MS", nodePolling);
        envVars.put("SELENIUM_NODE_PROXY_PARAMS", "de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy");
        if (BrowserType.CHROME.equalsIgnoreCase(browser)) {
            envVars.put("SELENIUM_NODE_CH_PORT", String.valueOf(nodePort));
            envVars.put("CHROME", "true");
        } else {
            envVars.put("CHROME", "false");
        }
        if (BrowserType.FIREFOX.equalsIgnoreCase(browser)) {
            envVars.put("SELENIUM_NODE_FF_PORT", String.valueOf(nodePort));
            envVars.put("FIREFOX", "true");
        } else {
            envVars.put("FIREFOX", "false");
        }
        return envVars;
    }

    private void createContainersOnStartup() {
        int configuredContainers = getChromeContainersOnStartup() + getFirefoxContainersOnStartup();
        int containersToCreate = configuredContainers > getMaxDockerSeleniumContainers() ?
                getMaxDockerSeleniumContainers() : configuredContainers;
        LOGGER.log(Level.INFO, String.format("%s Setting up %s nodes...", LOGGING_PREFIX, configuredContainers));
        // Thread.sleep() is to avoid having containers starting at the same time
        for (int i = 0; i < containersToCreate; i++) {
            if (i < getChromeContainersOnStartup()) {
                new Thread(() -> {
                    try {
                        Thread.sleep(RandomUtils.nextInt(1, (containersToCreate / 2) + 1) * sleepIntervalMultiplier);
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.FINE, getId() + " Error sleeping before starting a Chrome container", e);
                    }
                    startDockerSeleniumContainer(BrowserType.CHROME, getConfiguredTimeZone(), getConfiguredScreenSize(),
                            true);
                }).start();
            } else {
                new Thread(() -> {
                    try {
                        Thread.sleep(RandomUtils.nextInt(1, (containersToCreate / 2) + 1) * sleepIntervalMultiplier);
                    } catch (InterruptedException e) {
                        LOGGER.log(Level.FINE, getId() + " Error sleeping before starting a Firefox container", e);
                    }
                    startDockerSeleniumContainer(BrowserType.FIREFOX, getConfiguredTimeZone(), getConfiguredScreenSize(),
                            true);
                }).start();
            }
        }
        LOGGER.log(Level.INFO, String.format("%s containers were created, it will take a bit more until all get registered.", containersToCreate));
    }

    /*
        This method will search for a screenResolution capability to be passed when creating a docker-selenium node.
    */
    private Dimension getConfiguredScreenResolutionFromCapabilities(Map<String, Object> requestedCapability) {
        Dimension screenSize = getConfiguredScreenSize();
        String[] screenResolutionNames = {"screenResolution", "resolution", "screen-resolution"};
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
        String timeZoneName = "tz";
        TimeZone timeZone = getConfiguredTimeZone();
        if (requestedCapability.containsKey(timeZoneName)) {
            String timeZoneFromCapabilities = requestedCapability.get(timeZoneName).toString();
            if (Arrays.asList(TimeZone.getAvailableIDs()).contains(timeZoneFromCapabilities)) {
                timeZone = TimeZone.getTimeZone(timeZoneFromCapabilities);
            }
        }
        return timeZone;
    }

    private boolean testSessionsAvailable(Map<String, Object> requestedCapability) {
        for (RemoteProxy remoteProxy : this.getRegistry().getAllProxies()) {
            if (remoteProxy instanceof DockerSeleniumRemoteProxy) {
                DockerSeleniumRemoteProxy proxy = (DockerSeleniumRemoteProxy) remoteProxy;
                // If there are still available sessions to be used
                if (!proxy.isTestSessionLimitReached() && proxy.hasCapability(requestedCapability)) {
                    LOGGER.log(Level.FINE, LOGGING_PREFIX + "Sessions still available.");
                    return true;
                }
            }
        }
        LOGGER.log(Level.FINE, LOGGING_PREFIX + "No sessions available, a new node should be created.");
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

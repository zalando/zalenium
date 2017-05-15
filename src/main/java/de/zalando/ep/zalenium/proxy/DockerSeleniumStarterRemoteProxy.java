package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import de.zalando.ep.zalenium.util.DockerSeleniumCapabilityMatcher;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.listeners.RegistrationListener;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.beta.WebProxyHtmlRendererBeta;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
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

    @VisibleForTesting
    static final int DEFAULT_AMOUNT_CHROME_CONTAINERS = 0;
    @VisibleForTesting
    static final int DEFAULT_AMOUNT_FIREFOX_CONTAINERS = 0;
    @VisibleForTesting
    static final int DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING = 10;
    @VisibleForTesting
    static final String DEFAULT_TZ = "Europe/Berlin";
    @VisibleForTesting
    static final int DEFAULT_SCREEN_WIDTH = 1900;
    @VisibleForTesting
    static final int DEFAULT_SCREEN_HEIGHT = 1880;
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
    @VisibleForTesting
    private static final String DEFAULT_ZALENIUM_CONTAINER_NAME = "zalenium";
    @VisibleForTesting
    private static final String ZALENIUM_CONTAINER_NAME = "ZALENIUM_CONTAINER_NAME";
    private static final Logger LOGGER = Logger.getLogger(DockerSeleniumStarterRemoteProxy.class.getName());
    private static final String DOCKER_SELENIUM_IMAGE = "elgalu/selenium";
    private static final int LOWER_PORT_BOUNDARY = 40000;
    private static final int UPPER_PORT_BOUNDARY = 49999;
    private static final DockerClient defaultDockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    private static final Environment defaultEnvironment = new Environment();
    private static final String LOGGING_PREFIX = "[DS] ";
    private static List<DesiredCapabilities> dockerSeleniumCapabilities = new ArrayList<>();
    private static DockerClient dockerClient = defaultDockerClient;
    private static Environment env = defaultEnvironment;
    private static GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private static int chromeContainersOnStartup;
    private static int firefoxContainersOnStartup;
    private static int maxDockerSeleniumContainers;
    private static String configuredTimeZone;
    private static String timeZone;
    private static int configuredScreenWidth;
    private static int screenWidth;
    private static int configuredScreenHeight;
    private static int screenHeight;
    private static String containerName;
    private final HtmlRenderer renderer = new WebProxyHtmlRendererBeta(this);
    private List<Integer> allocatedPorts = new ArrayList<>();
    private boolean setupCompleted;
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

        int sWidth = env.getIntEnvVariable(ZALENIUM_SCREEN_WIDTH, DEFAULT_SCREEN_WIDTH);
        setScreenWidth(sWidth);
        setConfiguredScreenWidth(sWidth);

        int sHeight = env.getIntEnvVariable(ZALENIUM_SCREEN_HEIGHT, DEFAULT_SCREEN_HEIGHT);
        setScreenHeight(sHeight);
        setConfiguredScreenHeight(sHeight);

        String tz = env.getStringEnvVariable(ZALENIUM_TZ, DEFAULT_TZ);
        setTimeZone(tz);
        setConfiguredTimeZone(tz);

        String containerN = env.getStringEnvVariable(ZALENIUM_CONTAINER_NAME, DEFAULT_ZALENIUM_CONTAINER_NAME);
        setContainerName(containerN);
    }

    /*
     *  Updating the proxy's registration request information with the current DockerSelenium capabilities.
     *  If it is not possible to retrieve them, then we default to Chrome and Firefox in Linux.
     */
    @VisibleForTesting
    protected static RegistrationRequest updateDSCapabilities(RegistrationRequest registrationRequest) {
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

        String chromeVersion = "";
        String firefoxVersion = "";

        // Getting versions from the current docker-selenium image
        try {
            String latestDownloadedImage = getLatestDownloadedImage(DOCKER_SELENIUM_IMAGE);
            ImageInfo imageInfo = dockerClient.inspectImage(latestDownloadedImage);
            chromeVersion = imageInfo.config().labels().get("selenium3_chrome_version");
            firefoxVersion = imageInfo.config().labels().get("selenium3_firefox_version");
        } catch (DockerException | InterruptedException e) {
            LOGGER.log(Level.FINE, LOGGING_PREFIX + "Could not grab browser version information from the " +
                    "docker-selenium image", e);
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
    static void setDockerClient(final DockerClient client) {
        dockerClient = client;
    }

    @VisibleForTesting
    static void restoreDockerClient() {
        dockerClient = defaultDockerClient;
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

    @VisibleForTesting
    protected static String getTimeZone() {
        return timeZone;
    }

    @VisibleForTesting
    protected static void setTimeZone(String timeZone) {
        if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(timeZone)) {
            LOGGER.log(Level.WARNING, () -> String.format("%s is not a real time zone.", timeZone));
        }
        DockerSeleniumStarterRemoteProxy.timeZone = Arrays.asList(TimeZone.getAvailableIDs()).contains(timeZone) ?
                timeZone : DEFAULT_TZ;
    }

    @VisibleForTesting
    protected static int getScreenWidth() {
        return screenWidth;
    }

    @VisibleForTesting
    protected static void setScreenWidth(int screenWidth) {
        DockerSeleniumStarterRemoteProxy.screenWidth = screenWidth <= 0 ? DEFAULT_SCREEN_WIDTH : screenWidth;
    }

    @VisibleForTesting
    protected static int getScreenHeight() {
        return screenHeight;
    }

    @VisibleForTesting
    protected static void setScreenHeight(int screenHeight) {
        DockerSeleniumStarterRemoteProxy.screenHeight = screenHeight <= 0 ? DEFAULT_SCREEN_HEIGHT : screenHeight;
    }

    public static int getConfiguredScreenWidth() {
        return configuredScreenWidth <= 0 ? DEFAULT_SCREEN_WIDTH : configuredScreenWidth;
    }

    public static void setConfiguredScreenWidth(int configuredScreenWidth) {
        DockerSeleniumStarterRemoteProxy.configuredScreenWidth = configuredScreenWidth;
    }

    public static int getConfiguredScreenHeight() {
        return configuredScreenHeight <= 0 ? DEFAULT_SCREEN_HEIGHT : configuredScreenHeight;
    }

    public static void setConfiguredScreenHeight(int configuredScreenHeight) {
        DockerSeleniumStarterRemoteProxy.configuredScreenHeight = configuredScreenHeight;
    }

    public static String getConfiguredTimeZone() {
        return configuredTimeZone == null ? DEFAULT_TZ : configuredTimeZone;
    }

    public static void setConfiguredTimeZone(String configuredTimeZone) {
        DockerSeleniumStarterRemoteProxy.configuredTimeZone = configuredTimeZone;
    }

    @VisibleForTesting
    protected static void setEnv(final Environment env) {
        DockerSeleniumStarterRemoteProxy.env = env;
    }

    @VisibleForTesting
    static void restoreEnvironment() {
        env = defaultEnvironment;
    }

    @SuppressWarnings("ConstantConditions")
    private static String getLatestDownloadedImage(String imageName) throws DockerException, InterruptedException {
        List<Image> images = dockerClient.listImages(DockerClient.ListImagesParam.byName(imageName));
        if (images.isEmpty()) {
            LOGGER.log(Level.SEVERE, "A downloaded docker-selenium image was not found!");
            return DOCKER_SELENIUM_IMAGE;
        }
        for (int i = images.size() - 1; i >= 0; i--) {
            if (images.get(i).repoTags() == null) {
                images.remove(i);
            }
        }
        images.sort((o1, o2) -> o2.created().compareTo(o1.created()));
        return images.get(0).repoTags().get(0);
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
        configureScreenResolutionFromCapabilities(requestedCapability);

        // Check and configure time zone capabilities when they have been passed in the test config.
        configureTimeZoneFromCapabilities(requestedCapability);

        String browserName = requestedCapability.get(CapabilityType.BROWSER_NAME).toString();

        /*
            Here a docker-selenium container will be started and it will register to the hub
            We check first if a node has been created for this request already. If so, we skip it
            but increment the number of times it has been received. In case something went wrong with the node
            creation, we remove the mark* after 10 times and we create a node again.
            * The mark is an added custom capability
         */
        String waitingForNode = String.format("waitingFor_%s_Node", browserName.toUpperCase());
        if (!requestedCapability.containsKey(waitingForNode)) {
            LOGGER.log(Level.INFO, LOGGING_PREFIX + "Starting new node for {0}.", requestedCapability);
            if (startDockerSeleniumContainer(browserName)) {
                requestedCapability.put(waitingForNode, 1);
            }
        } else {
            int attempts = (int) requestedCapability.get(waitingForNode);
            attempts++;
            if (attempts >= 20) {
                LOGGER.log(Level.FINE, LOGGING_PREFIX + "Request has waited 20 attempts for a node, something " +
                        "went wrong with the previous attempts, creating a new node for {0}.", requestedCapability);
                startDockerSeleniumContainer(browserName, true);
                requestedCapability.put(waitingForNode, 1);
            } else {
                requestedCapability.put(waitingForNode, attempts);
                LOGGER.log(Level.FINE, LOGGING_PREFIX + "Request waiting for a node new node for {0}.", requestedCapability);
            }
        }
        return null;
    }

    /*
        Starting a few containers (Firefox, Chrome), so they are ready when the tests come.
        Executed in a thread so we don't wait for the containers to be created and the node
        registration is not delayed.
    */
    @Override
    public void beforeRegistration() {
        readConfigurationFromEnvVariables();
        setupCompleted = false;
        createStartupContainers();
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
    public boolean startDockerSeleniumContainer(String browser) {
        return startDockerSeleniumContainer(browser, false);
    }

    @VisibleForTesting
    public boolean startDockerSeleniumContainer(String browser, boolean forceCreation) {

        if (validateAmountOfDockerSeleniumContainers() || forceCreation) {

            String hostIpAddress = "localhost";

            /*
                Building the docker command, depending if Chrome or Firefox is requested.
                To launch only the requested node type.
             */

            final int nodePort = findFreePortInRange(LOWER_PORT_BOUNDARY, UPPER_PORT_BOUNDARY);
            final int vncPort = nodePort + 10000;

            List<String> envVariables = new ArrayList<>();
            envVariables.add("ZALENIUM=true");
            envVariables.add("SELENIUM_HUB_HOST=" + hostIpAddress);
            envVariables.add("SELENIUM_HUB_PORT=4445");
            envVariables.add("SELENIUM_NODE_HOST=" + hostIpAddress);
            envVariables.add("GRID=false");
            envVariables.add("RC_CHROME=false");
            envVariables.add("RC_FIREFOX=false");
            envVariables.add("USE_SELENIUM=3");
            envVariables.add("WAIT_TIMEOUT=120s");
            envVariables.add("PICK_ALL_RANDOM_PORTS=true");
            envVariables.add("VIDEO_STOP_SLEEP_SECS=1");
            envVariables.add("WAIT_TIME_OUT_VIDEO_STOP=20s");
            boolean sendAnonymousUsageInfo = env.getBooleanEnvVariable("ZALENIUM_SEND_ANONYMOUS_USAGE_INFO", false);
            envVariables.add("SEND_ANONYMOUS_USAGE_INFO=" + sendAnonymousUsageInfo);
            envVariables.add("BUILD_URL=" + env.getStringEnvVariable("BUILD_URL", ""));
            envVariables.add("NOVNC=true");
            envVariables.add("NOVNC_PORT=" + vncPort);
            envVariables.add("SCREEN_WIDTH=" + getScreenWidth());
            envVariables.add("SCREEN_HEIGHT=" + getScreenHeight());
            envVariables.add("TZ=" + getTimeZone());
            envVariables.add("SELENIUM_NODE_REGISTER_CYCLE=0");
            envVariables.add("SELENIUM_NODE_PROXY_PARAMS=de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy");
            if (BrowserType.CHROME.equalsIgnoreCase(browser)) {
                envVariables.add("SELENIUM_NODE_CH_PORT=" + nodePort);
                envVariables.add("CHROME=true");
            } else {
                envVariables.add("CHROME=false");
            }
            if (BrowserType.FIREFOX.equalsIgnoreCase(browser)) {
                envVariables.add("SELENIUM_NODE_FF_PORT=" + nodePort);
                envVariables.add("FIREFOX=true");
            } else {
                envVariables.add("FIREFOX=false");
            }

            String networkMode = String.format("container:%s", getContainerName());
            HostConfig hostConfig = HostConfig.builder()
                    .networkMode(networkMode)
                    .appendBinds("/dev/shm:/dev/shm")
                    .appendBinds("/tmp/mounted:/tmp/mounted")
                    .autoRemove(true)
                    .build();

            try {
                final ContainerConfig containerConfig = ContainerConfig.builder()
                        .image(getLatestDownloadedImage(DOCKER_SELENIUM_IMAGE))
                        .env(envVariables)
                        .hostConfig(hostConfig)
                        .build();

                String dockerSeleniumContainerName = String.format("%s_%s", getContainerName(), nodePort);
                final ContainerCreation dockerSeleniumContainer = dockerClient.createContainer(containerConfig,
                        dockerSeleniumContainerName);
                dockerClient.startContainer(dockerSeleniumContainer.id());
                // TODO: docker exec dockerSeleniumContainer.id() wait_all_done 30s
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, LOGGING_PREFIX + e.toString(), e);
                ga.trackException(e);
            }
        }
        return false;
    }

    @VisibleForTesting
    protected boolean isSetupCompleted() {
        return setupCompleted;
    }

    private void createStartupContainers() {
        int configuredContainers = getChromeContainersOnStartup() + getFirefoxContainersOnStartup();
        int containersToCreate = configuredContainers > getMaxDockerSeleniumContainers() ?
                getMaxDockerSeleniumContainers() : configuredContainers;
        new Thread(() -> {
            LOGGER.log(Level.INFO, String.format("%s Setting up %s nodes...", LOGGING_PREFIX, configuredContainers));
            int createdContainers = 0;
            while (createdContainers < containersToCreate && getNumberOfRunningContainers() <= getMaxDockerSeleniumContainers()) {
                boolean wasContainerCreated;
                if (createdContainers < getChromeContainersOnStartup()) {
                    wasContainerCreated = startDockerSeleniumContainer(BrowserType.CHROME);
                } else {
                    wasContainerCreated = startDockerSeleniumContainer(BrowserType.FIREFOX);
                }
                createdContainers = wasContainerCreated ? createdContainers + 1 : createdContainers;
            }
            LOGGER.log(Level.INFO, String.format("%s containers were created, it will take a bit more until all get registered.", createdContainers));
            setupCompleted = true;
        }).start();
    }

    /*
        This method will search for a screenResolution capability to be passed when creating a docker-selenium node.
    */
    private void configureScreenResolutionFromCapabilities(Map<String, Object> requestedCapability) {
        boolean wasConfiguredScreenWidthAndHeightChanged = false;
        String[] screenResolutionNames = {"screenResolution", "resolution", "screen-resolution"};
        for (String screenResolutionName : screenResolutionNames) {
            if (requestedCapability.containsKey(screenResolutionName)) {
                String screenResolution = requestedCapability.get(screenResolutionName).toString();
                try {
                    int screenWidth = Integer.parseInt(screenResolution.split("x")[0]);
                    int screenHeight = Integer.parseInt(screenResolution.split("x")[1]);
                    if (screenWidth > 0 && screenHeight > 0) {
                        setScreenHeight(screenHeight);
                        setScreenWidth(screenWidth);
                        wasConfiguredScreenWidthAndHeightChanged = true;
                    } else {
                        setScreenWidth(getConfiguredScreenWidth());
                        setScreenHeight(getConfiguredScreenHeight());
                        LOGGER.log(Level.FINE, "One of the values provided for screenResolution is negative, " +
                                "defaults will be used. Passed value -> " + screenResolution);
                    }
                } catch (Exception e) {
                    setScreenWidth(getConfiguredScreenWidth());
                    setScreenHeight(getConfiguredScreenHeight());
                    LOGGER.log(Level.FINE, "Values provided for screenResolution are not valid integers or " +
                            "either the width or the height is missing, defaults will be used. Passed value -> "
                            + screenResolution);
                }
            }
        }
        // If the screen resolution parameters were not changed, we just set the defaults again.
        // Also in the capabilities, to avoid the situation where a request grabs the node from other request
        // just because the platform, version, and browser match.
        if (!wasConfiguredScreenWidthAndHeightChanged) {
            setScreenWidth(getConfiguredScreenWidth());
            setScreenHeight(getConfiguredScreenHeight());
            String screenResolution = String.format("%sx%s", getScreenWidth(), getScreenHeight());
            requestedCapability.put("screenResolution", screenResolution);
        }
    }

    /*
    This method will search for a tz capability to be passed when creating a docker-selenium node.
    */
    private void configureTimeZoneFromCapabilities(Map<String, Object> requestedCapability) {
        boolean wasConfiguredTimeZoneChanged = false;
        String timeZoneName = "tz";
        if (requestedCapability.containsKey(timeZoneName)) {
            String timeZone = requestedCapability.get(timeZoneName).toString();
            setTimeZone(timeZone);
            wasConfiguredTimeZoneChanged = true;
        }
        // If the time zone parameter was not changed, we just set the defaults again.
        if (!wasConfiguredTimeZoneChanged) {
            setTimeZone(getConfiguredTimeZone());
            requestedCapability.put(timeZoneName, getConfiguredTimeZone());
        }
    }

    private int getNumberOfRunningContainers() {
        try {
            List<Container> containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
            int numberOfDockerSeleniumContainers = 0;
            for (Container container : containerList) {
                if (container.image().contains(DOCKER_SELENIUM_IMAGE) && !"exited".equalsIgnoreCase(container.state())) {
                    numberOfDockerSeleniumContainers++;
                }
            }
            return numberOfDockerSeleniumContainers;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, LOGGING_PREFIX + e.toString(), e);
            ga.trackException(e);
        }
        return 0;
    }

    private boolean validateAmountOfDockerSeleniumContainers() {
        try {
            int numberOfDockerSeleniumContainers = getNumberOfRunningContainers();

            /*
                Validation to avoid the situation where 20 containers are running and only 4 proxies are registered.
                The remaining 16 are not registered because they are all trying to do it and the hub just cannot
                process all the registrations fast enough, causing many unexpected errors.
            */
            int numberOfProxies = 0;
            for (RemoteProxy remoteProxy : getRegistry().getAllProxies()) {
                if (remoteProxy instanceof DockerSeleniumRemoteProxy) {
                    numberOfProxies++;
                }
            }
            LOGGER.log(Level.FINE, "Number of proxies -> " + numberOfProxies);
            int newSessionRequestCount = getRegistry().getNewSessionRequestCount();
            LOGGER.log(Level.FINE, "(getNewSessionRequestCount()) -> " + newSessionRequestCount);
            int proxiesAndNewSessions = numberOfProxies + newSessionRequestCount;
            LOGGER.log(Level.FINE, "Number of proxies + new sessions requested -> " + proxiesAndNewSessions);
            LOGGER.log(Level.FINE, "Number of DockerSelenium containers -> " + numberOfDockerSeleniumContainers);
            if (numberOfDockerSeleniumContainers > proxiesAndNewSessions) {
                LOGGER.log(Level.FINE, LOGGING_PREFIX + "More docker-selenium containers running than proxies, {0} vs. {1}",
                        new Object[]{numberOfDockerSeleniumContainers, numberOfProxies});
                Thread.sleep(500);
                return false;
            }

            LOGGER.log(Level.FINE, () -> String.format("%s %s docker-selenium containers running", LOGGING_PREFIX,
                    numberOfDockerSeleniumContainers));
            if (numberOfDockerSeleniumContainers >= getMaxDockerSeleniumContainers()) {
                LOGGER.log(Level.WARNING, LOGGING_PREFIX + "Max. number of docker-selenium containers has been reached, " +
                        "no more will be created until the number decreases below {0}.", getMaxDockerSeleniumContainers());
                return false;
            }
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
        if (allocatedPorts.size() > (upperBoundary - lowerBoundary - 200)) {
            allocatedPorts.clear();
            LOGGER.log(Level.INFO, () -> LOGGING_PREFIX + "Cleaning allocated ports list.");
        }
        for (int portNumber = lowerBoundary; portNumber <= upperBoundary; portNumber++) {
            if (!allocatedPorts.contains(portNumber)) {
                int freePort = -1;

                try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
                    freePort = serverSocket.getLocalPort();
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, LOGGING_PREFIX + e.toString(), e);
                }

                if (freePort != -1) {
                    allocatedPorts.add(freePort);
                    return freePort;
                }
            }
        }
        return -1;
    }

}

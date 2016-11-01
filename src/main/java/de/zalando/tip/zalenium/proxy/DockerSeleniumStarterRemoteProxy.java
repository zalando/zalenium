package de.zalando.tip.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import de.zalando.tip.zalenium.util.CommonProxyUtilities;
import de.zalando.tip.zalenium.util.Environment;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.listeners.RegistrationListener;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The idea of this proxy instance is:
 * 1. Receive a session request with some requested capabilities
 * 2. Start a docker-selenium container that will register with the hub
 * 3. Reject the received request
 * 4. When the registry receives the rejected request and sees the new registered node from step 2,
 *    the process will flow as normal.
 *
 */

public class DockerSeleniumStarterRemoteProxy extends DefaultRemoteProxy implements RegistrationListener {

    private static final Logger LOGGER = Logger.getLogger(DockerSeleniumStarterRemoteProxy.class.getName());

    private static final String DOCKER_SELENIUM_IMAGE = "elgalu/selenium";
    private static final String DOCKER_SELENIUM_CAPABILITIES_URL = "https://raw.githubusercontent.com/elgalu/docker-selenium/latest/capabilities.json";

    private static List<DesiredCapabilities> dockerSeleniumCapabilities = new ArrayList<>();

    private static final int LOWER_PORT_BOUNDARY = 40000;
    private static final int UPPER_PORT_BOUNDARY = 50000;


    private static List<Integer> allocatedPorts = new ArrayList<>();

    private static final DockerClient defaultDockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    private static DockerClient dockerClient = defaultDockerClient;

    private static final Environment defaultEnvironment = new Environment();
    private static Environment environment = defaultEnvironment;

    @VisibleForTesting
    protected static final int DEFAULT_AMOUNT_CHROME_CONTAINERS = 0;
    @VisibleForTesting
    protected static final int DEFAULT_AMOUNT_FIREFOX_CONTAINERS = 0;
    @VisibleForTesting
    protected static final int DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING = 10;

    @VisibleForTesting
    protected static final String ZALENIUM_CHROME_CONTAINERS = "ZALENIUM_CHROME_CONTAINERS";
    @VisibleForTesting
    protected static final String ZALENIUM_FIREFOX_CONTAINERS = "ZALENIUM_FIREFOX_CONTAINERS";
    @VisibleForTesting
    protected static final String ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS = "ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS";

    private static final String LOGGING_PREFIX = "[DS] ";

    /*
        Amount of containers launched when the proxy is starting.
     */
    private static int chromeContainersOnStartup;
    private static int firefoxContainersOnStartup;

    /*
            Max amount of docker Selenium containers running at the same time.
         */
    private static int maxDockerSeleniumContainers;


    public DockerSeleniumStarterRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateDSCapabilities(request, DOCKER_SELENIUM_CAPABILITIES_URL), registry);
    }

    /**
     *  Receives a request to create a new session, but instead of accepting it, it will create a
     *  docker-selenium container which will register to the hub, then reject the request and the hub
     *  will assign the request to the new registered node.
     */
    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {

        if (!hasCapability(requestedCapability)) {
            LOGGER.log(Level.FINE, LOGGING_PREFIX + "Capability not supported {0}", requestedCapability);
            return null;
        }

        LOGGER.log(Level.INFO, LOGGING_PREFIX + "Starting new node for {0}.", requestedCapability);

        String browserName = requestedCapability.get(CapabilityType.BROWSER_NAME).toString();

        /*
            Here a docker-selenium container will be started and it will register to the hub
         */
        startDockerSeleniumContainer(browserName);
        return null;
    }

    /*
        Starting a few containers (Firefox, Chrome), so they are ready when the tests come.
    */
    @Override
    public void beforeRegistration() {
        readConfigurationFromEnvVariables();
        if (getChromeContainersOnStartup() > 0 || getFirefoxContainersOnStartup() > 0) {
            LOGGER.log(Level.INFO, LOGGING_PREFIX + "Setting up {0} Firefox nodes and {1} Chrome nodes ready to use.",
                    new Object[]{getFirefoxContainersOnStartup(), getChromeContainersOnStartup()});
            for (int i = 0; i < getChromeContainersOnStartup(); i++) {
                startDockerSeleniumContainer(BrowserType.CHROME);
            }
            for (int i = 0; i < getFirefoxContainersOnStartup(); i++) {
                startDockerSeleniumContainer(BrowserType.FIREFOX);
            }
        }
    }

    /*
        Making the node seem as heavily used, in order to get it listed after the 'docker-selenium' nodes.
        98% used.
     */
    @Override
    public float getResourceUsageInPercent() {
        return 98;
    }

    /*
     * Reading configuration values from the environment variables, if a value was not provided it falls back to defaults.
     */
    private static void readConfigurationFromEnvVariables() {

        String envVarIsNotSetMessage = LOGGING_PREFIX + "Env. variable %s value is not set, falling back to default: %s.";
        String envVarIsNotAValidIntMessage = LOGGING_PREFIX + "Env. variable %s is not a valid integer.";

        if (environment.getEnvVariable(ZALENIUM_CHROME_CONTAINERS) != null) {
            try {
                int chromeContainers = Integer.parseInt(environment.getEnvVariable(ZALENIUM_CHROME_CONTAINERS));
                setChromeContainersOnStartup(chromeContainers);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, String.format(envVarIsNotAValidIntMessage, ZALENIUM_CHROME_CONTAINERS), e);
                setChromeContainersOnStartup(DEFAULT_AMOUNT_CHROME_CONTAINERS);
            }
        } else {
            LOGGER.log(Level.FINE, String.format(envVarIsNotSetMessage, ZALENIUM_CHROME_CONTAINERS,
                    DEFAULT_AMOUNT_CHROME_CONTAINERS));
            setChromeContainersOnStartup(DEFAULT_AMOUNT_CHROME_CONTAINERS);
        }

        if (environment.getEnvVariable(ZALENIUM_FIREFOX_CONTAINERS) != null) {
            try {
                int firefoxContainers = Integer.parseInt(environment.getEnvVariable(ZALENIUM_FIREFOX_CONTAINERS));
                setFirefoxContainersOnStartup(firefoxContainers);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, String.format(envVarIsNotAValidIntMessage, ZALENIUM_FIREFOX_CONTAINERS), e);
                setFirefoxContainersOnStartup(DEFAULT_AMOUNT_FIREFOX_CONTAINERS);
            }
        } else {
            LOGGER.log(Level.FINE, String.format(envVarIsNotSetMessage, ZALENIUM_FIREFOX_CONTAINERS,
                    DEFAULT_AMOUNT_FIREFOX_CONTAINERS));
            setFirefoxContainersOnStartup(DEFAULT_AMOUNT_FIREFOX_CONTAINERS);
        }

        if (environment.getEnvVariable(ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS) != null) {
            try {
                int maxDSContainers = Integer.parseInt(environment.getEnvVariable(ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS));
                setMaxDockerSeleniumContainers(maxDSContainers);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, String.format(envVarIsNotAValidIntMessage, ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS), e);
                setMaxDockerSeleniumContainers(DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING);
            }
        } else {
            LOGGER.log(Level.FINE, String.format(envVarIsNotSetMessage, ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS,
                    DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING));
            setMaxDockerSeleniumContainers(DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING);
        }
    }

    /*
     *  Updating the proxy's registration request information with the current DockerSelenium capabilities.
     *  If it is not possible to retrieve them, then we default to Chrome and Firefox in Linux.
     */
    @VisibleForTesting
    protected static RegistrationRequest updateDSCapabilities(RegistrationRequest registrationRequest, String url) {
        registrationRequest.getCapabilities().clear();
        registrationRequest.getCapabilities().addAll(getCapabilities(url));
        return registrationRequest;
    }

    public static List<DesiredCapabilities> getCapabilities(String url) {
        if (!dockerSeleniumCapabilities.isEmpty()) {
            return dockerSeleniumCapabilities;
        }

        dockerSeleniumCapabilities = getDockerSeleniumCapabilitiesFromGitHub(url);
        if (dockerSeleniumCapabilities.isEmpty()) {
            dockerSeleniumCapabilities = getDockerSeleniumFallbackCapabilities();
            LOGGER.log(Level.WARNING, LOGGING_PREFIX + "Could not fetch capabilities from {0}, falling back to defaults.", url);
            return dockerSeleniumCapabilities;
        }
        LOGGER.log(Level.INFO, LOGGING_PREFIX + "Capabilities fetched from {0}", url);
        return dockerSeleniumCapabilities;
    }

    @VisibleForTesting
    protected void startDockerSeleniumContainer(String browser) {

        if (validateAmountOfDockerSeleniumContainers()) {

            String hostIpAddress = "localhost";

            /*
                Building the docker command, depending if Chrome or Firefox is requested.
                To launch only the requested node type.
             */

            final int nodePort = findFreePortInRange(LOWER_PORT_BOUNDARY, UPPER_PORT_BOUNDARY);

            List<String> envVariables = new ArrayList<>();
            envVariables.add("SELENIUM_HUB_HOST=" + hostIpAddress);
            envVariables.add("SELENIUM_HUB_PORT=4444");
            envVariables.add("SELENIUM_NODE_HOST=" + hostIpAddress);
            envVariables.add("GRID=false");
            envVariables.add("RC_CHROME=false");
            envVariables.add("RC_FIREFOX=false");
            envVariables.add("WAIT_TIMEOUT=20s");
            envVariables.add("PICK_ALL_RANDMON_PORTS=true");
            envVariables.add("VIDEO_STOP_SLEEP_SECS=4");
            envVariables.add("SELENIUM_NODE_REGISTER_CYCLE=0");
            envVariables.add("SELENIUM_NODE_PROXY_PARAMS=de.zalando.tip.zalenium.proxy.DockerSeleniumRemoteProxy");
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

            HostConfig hostConfig = HostConfig.builder()
                    .appendBinds("/dev/shm:/dev/shm")
                    .networkMode("container:zalenium")
                    .build();

            try {
                final ContainerConfig containerConfig = ContainerConfig.builder()
                        .image(getLatestDownloadedImage(DOCKER_SELENIUM_IMAGE))
                        .env(envVariables)
                        .hostConfig(hostConfig)
                        .build();

                String containerName = String.format("%s_%s", "ZALENIUM", nodePort);
                final ContainerCreation dockerSeleniumContainer = dockerClient.createContainer(containerConfig,
                        containerName);
                dockerClient.startContainer(dockerSeleniumContainer.id());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, LOGGING_PREFIX + e.toString(), e);
            }
        }
        /*
            Temporal method implemented until https://github.com/spotify/docker-client/issues/488 gets solved.
         */
        removeExitedDockerSeleniumContainers();
    }

    private String getLatestDownloadedImage(String imageName) throws DockerException, InterruptedException {
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

    @VisibleForTesting
    protected static void setDockerClient(final DockerClient client) {
        dockerClient = client;
    }

    @VisibleForTesting
    protected static void restoreDockerClient() {
        dockerClient = defaultDockerClient;
    }

    @VisibleForTesting
    protected static void setEnvironment(final Environment env) {
        environment = env;
    }

    protected static void restoreEnvironment() {
        environment = defaultEnvironment;
    }

    public static int getFirefoxContainersOnStartup() {
        return firefoxContainersOnStartup;
    }

    public static void setFirefoxContainersOnStartup(int firefoxContainersOnStartup) {
        DockerSeleniumStarterRemoteProxy.firefoxContainersOnStartup = firefoxContainersOnStartup < 0 ?
                DEFAULT_AMOUNT_FIREFOX_CONTAINERS : firefoxContainersOnStartup;
    }

    public static int getChromeContainersOnStartup() {
        return chromeContainersOnStartup;
    }

    public static void setChromeContainersOnStartup(int chromeContainersOnStartup) {
        DockerSeleniumStarterRemoteProxy.chromeContainersOnStartup = chromeContainersOnStartup < 0 ?
                DEFAULT_AMOUNT_CHROME_CONTAINERS : chromeContainersOnStartup;
    }

    public static int getMaxDockerSeleniumContainers() {
        return maxDockerSeleniumContainers;
    }

    public static void setMaxDockerSeleniumContainers(int maxDockerSeleniumContainers) {
        DockerSeleniumStarterRemoteProxy.maxDockerSeleniumContainers = maxDockerSeleniumContainers < 0 ?
                DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING : maxDockerSeleniumContainers;
    }

    private static List<DesiredCapabilities> getDockerSeleniumCapabilitiesFromGitHub(String url) {
        JsonElement dsCapabilities = CommonProxyUtilities.readJSONFromUrl(url);
        List<DesiredCapabilities> desiredCapabilitiesArrayList = new ArrayList<>();
        try {
            if (dsCapabilities != null) {
                for (JsonElement cap : dsCapabilities.getAsJsonObject().getAsJsonArray("caps")) {
                    JsonObject capAsJsonObject = cap.getAsJsonObject();
                    DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
                    desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
                    desiredCapabilities.setBrowserName(capAsJsonObject.get("BROWSER_NAME").getAsString());
                    desiredCapabilities.setPlatform(Platform.fromString(capAsJsonObject.get("PLATFORM").getAsString()));
                    desiredCapabilities.setVersion(capAsJsonObject.get("VERSION").getAsString());
                    desiredCapabilitiesArrayList.add(desiredCapabilities);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, LOGGING_PREFIX + e.toString(), e);
        }
        return desiredCapabilitiesArrayList;
    }

    @VisibleForTesting
    protected static List<DesiredCapabilities> getDockerSeleniumFallbackCapabilities() {
        List<DesiredCapabilities> dsCapabilities = new ArrayList<>();
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setBrowserName(BrowserType.FIREFOX);
        desiredCapabilities.setPlatform(Platform.LINUX);
        desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        dsCapabilities.add(desiredCapabilities);
        desiredCapabilities.setBrowserName(BrowserType.CHROME);
        desiredCapabilities.setPlatform(Platform.LINUX);
        desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        dsCapabilities.add(desiredCapabilities);
        return dsCapabilities;
    }

    /*
        Temporal method implemented until https://github.com/spotify/docker-client/issues/488 gets solved.
     */
    private void removeExitedDockerSeleniumContainers(){
        try {
            List<Container> containerList = dockerClient.listContainers(DockerClient.ListContainersParam.withStatusExited());
            for (Container container : containerList) {
                if (container.image().contains(DOCKER_SELENIUM_IMAGE)) {
                    dockerClient.removeContainer(container.id());
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, LOGGING_PREFIX + e.toString(), e);
        }
    }


    private boolean validateAmountOfDockerSeleniumContainers() {
        try {
            List<Container> containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
            int numberOfDockerSeleniumContainers = 0;
            for (Container container : containerList) {
                if (container.image().contains(DOCKER_SELENIUM_IMAGE) &&
                        !"exited".equalsIgnoreCase(container.status())) {
                    numberOfDockerSeleniumContainers++;
                }
            }

            /*
                Validation to avoid the situation where 20 containers are running and only 4 proxies are registered.
                The remaining 16 are not registered because they are all trying to do it and the hub just cannot
                process all the registrations fast enough, causing many unexpected errors.
            */
            int tolerableDifference = 4;
            int numberOfProxies = getRegistry().getAllProxies().size() + tolerableDifference;
            if (numberOfDockerSeleniumContainers > numberOfProxies) {
                LOGGER.log(Level.FINE, LOGGING_PREFIX + "More docker-selenium containers running than proxies, {0} vs. {1}",
                        new Object[]{numberOfDockerSeleniumContainers, numberOfProxies});
                Thread.sleep(500);
                return false;
            }

            LOGGER.log(Level.FINE, LOGGING_PREFIX + "{0} docker-selenium containers running", containerList.size());
            if (numberOfDockerSeleniumContainers >= getMaxDockerSeleniumContainers()) {
                LOGGER.log(Level.FINE, LOGGING_PREFIX + "Max. number of docker-selenium containers has been reached, no more " +
                        "will be created until the number decreases below {0}.", getMaxDockerSeleniumContainers());
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, LOGGING_PREFIX + e.toString(), e);
        }
        return false;
    }

    /*
        Method adapted from https://gist.github.com/vorburger/3429822
     */
    private static int findFreePortInRange(int lowerBoundary, int upperBoundary) throws IllegalStateException {
        for (int portNumber = lowerBoundary; portNumber <= upperBoundary; portNumber++) {
            if (!allocatedPorts.contains(portNumber)) {
                int freePort = -1;

                try(ServerSocket serverSocket = new ServerSocket(portNumber)) {
                    freePort = serverSocket.getLocalPort();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, LOGGING_PREFIX + e.toString(), e);
                }

                if (freePort != -1) {
                    allocatedPorts.add(freePort);
                    return freePort;
                }
            }
        }
        throw new IllegalStateException("Could not find a free TCP/IP port to use for docker-selenium");
    }

}

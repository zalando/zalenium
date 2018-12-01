package de.zalando.ep.zalenium.util;


import com.google.common.annotations.VisibleForTesting;

/**
 * Common configuration for Zalenium.
 */
public class ZaleniumConfiguration {


    @VisibleForTesting
    public static final int DEFAULT_AMOUNT_DESIRED_CONTAINERS = 1;
    @VisibleForTesting
    public static final int DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING = 10;
    @VisibleForTesting
    public static final int DEFAULT_TIME_TO_WAIT_TO_START = 180000;
    @VisibleForTesting
    public static final int DEFAULT_TIMES_TO_PROCESS_REQUEST = 30;
    @VisibleForTesting
    public static final String ZALENIUM_DESIRED_CONTAINERS = "ZALENIUM_DESIRED_CONTAINERS";
    @VisibleForTesting
    public static final String ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS = "ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS";
    private static final String WAIT_FOR_AVAILABLE_NODES = "WAIT_FOR_AVAILABLE_NODES";
    private static final String TIME_TO_WAIT_TO_START = "TIME_TO_WAIT_TO_START";
    private static final String MAX_TIMES_TO_PROCESS_REQUEST = "MAX_TIMES_TO_PROCESS_REQUEST";

    // Intended to start Zalenium locally for debugging or development. See ZaleniumRegistryTest#runLocally
    @VisibleForTesting
    private static final String ZALENIUM_RUNNING_LOCALLY_ENV_VAR = "runningLocally";
    private static final Environment defaultEnvironment = new Environment();
    public static boolean ZALENIUM_RUNNING_LOCALLY = false;
    @VisibleForTesting
    private static Environment env = defaultEnvironment;
    private static int desiredContainersOnStartup;
    private static int maxDockerSeleniumContainers;
    private static boolean waitForAvailableNodes;
    private static int timeToWaitToStart;
    private static int maxTimesToProcessRequest;

    static {
    	readConfigurationFromEnvVariables();
    }

    /*
     * Reading configuration values from the env variables, if a value was not provided it falls back to defaults.
     */
    @VisibleForTesting
    public static void readConfigurationFromEnvVariables() {

        int desiredContainers = env.getIntEnvVariable(ZALENIUM_DESIRED_CONTAINERS, DEFAULT_AMOUNT_DESIRED_CONTAINERS);
        setDesiredContainersOnStartup(desiredContainers);

        int maxDSContainers = env.getIntEnvVariable(ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS,
                DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING);
        setMaxDockerSeleniumContainers(maxDSContainers);

        ZALENIUM_RUNNING_LOCALLY = Boolean.valueOf(System.getProperty(ZALENIUM_RUNNING_LOCALLY_ENV_VAR));

        boolean waitForNodes = env.getBooleanEnvVariable(WAIT_FOR_AVAILABLE_NODES, true);
        setWaitForAvailableNodes(waitForNodes);

        int timeToWait = env.getIntEnvVariable(TIME_TO_WAIT_TO_START, DEFAULT_TIME_TO_WAIT_TO_START);
        setTimeToWaitToStart(timeToWait);

        int maxTimes = env.getIntEnvVariable(MAX_TIMES_TO_PROCESS_REQUEST, DEFAULT_TIMES_TO_PROCESS_REQUEST);
        setMaxTimesToProcessRequest(maxTimes);
    }

    public static int getMaxTimesToProcessRequest() {
        return maxTimesToProcessRequest;
    }

    public static void setMaxTimesToProcessRequest(int maxTimesToProcessRequest) {
        ZaleniumConfiguration.maxTimesToProcessRequest = maxTimesToProcessRequest < 0 ?
                    DEFAULT_TIMES_TO_PROCESS_REQUEST : maxTimesToProcessRequest;
    }

    public static boolean isWaitForAvailableNodes() {
      return waitForAvailableNodes;
    }

    @SuppressWarnings("WeakerAccess")
    public static void setWaitForAvailableNodes(boolean waitForAvailableNodes) {
      ZaleniumConfiguration.waitForAvailableNodes = waitForAvailableNodes;
    }

    public static int getTimeToWaitToStart() {
      return timeToWaitToStart;
    }

    public static void setTimeToWaitToStart(int timeToWaitToStart) {
      ZaleniumConfiguration.timeToWaitToStart = timeToWaitToStart < 0 ?
                DEFAULT_TIME_TO_WAIT_TO_START : timeToWaitToStart;
    }

    public static int getDesiredContainersOnStartup() {
        return desiredContainersOnStartup;
    }

    @VisibleForTesting
    public static void setDesiredContainersOnStartup(int desiredContainersOnStartup) {
        ZaleniumConfiguration.desiredContainersOnStartup = desiredContainersOnStartup < 0 ?
                DEFAULT_AMOUNT_DESIRED_CONTAINERS : desiredContainersOnStartup;
    }

    @VisibleForTesting
    public static int getMaxDockerSeleniumContainers() {
        return maxDockerSeleniumContainers;
    }

    @VisibleForTesting
    public static void setMaxDockerSeleniumContainers(int maxDockerSeleniumContainers) {
        ZaleniumConfiguration.maxDockerSeleniumContainers = maxDockerSeleniumContainers < 0 ?
                DEFAULT_AMOUNT_DOCKER_SELENIUM_CONTAINERS_RUNNING : maxDockerSeleniumContainers;
    }

    @VisibleForTesting
    public static void setEnv(final Environment env) {
        ZaleniumConfiguration.env = env;
    }

    @VisibleForTesting
    public static void restoreEnvironment() {
        env = defaultEnvironment;
    }


}

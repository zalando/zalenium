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
    public static final String ZALENIUM_DESIRED_CONTAINERS = "ZALENIUM_DESIRED_CONTAINERS";
    @VisibleForTesting
    public static final String ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS = "ZALENIUM_MAX_DOCKER_SELENIUM_CONTAINERS";

    // Intended to start Zalenium locally for debugging or development. See ZaleniumRegistryTest#runLocally
    @VisibleForTesting
    public static final String ZALENIUM_RUNNING_LOCALLY_ENV_VAR = "runningLocally";
    public static boolean ZALENIUM_RUNNING_LOCALLY = false;

    private static final Environment defaultEnvironment = new Environment();

    @VisibleForTesting
    private static Environment env = defaultEnvironment;
    private static int desiredContainersOnStartup;
    private static int maxDockerSeleniumContainers;

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
    }

    static {
    	readConfigurationFromEnvVariables();
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

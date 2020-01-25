package de.zalando.ep.zalenium.container.swarm;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import com.spotify.docker.client.messages.mount.Mount;
import com.spotify.docker.client.messages.swarm.*;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.proxy.DockeredSeleniumStarter;
import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@SuppressWarnings("ConstantConditions")
public class SwarmContainerClient implements ContainerClient {

    private static final String ZALENIUM_SELENIUM_CONTAINER_CPU_LIMIT = "ZALENIUM_SELENIUM_CONTAINER_CPU_LIMIT";
    private static final String ZALENIUM_SELENIUM_CONTAINER_MEMORY_LIMIT = "ZALENIUM_SELENIUM_CONTAINER_MEMORY_LIMIT";
    private static final String SWARM_RUN_TESTS_ONLY_ON_WORKERS = "SWARM_RUN_TESTS_ONLY_ON_WORKERS";

    private static final String SWARM_EXEC_IMAGE = "datagridsys/skopos-plugin-swarm-exec:latest";

    private static final Environment defaultEnvironment = new Environment();

    private static Environment env = defaultEnvironment;
    private static String seleniumContainerCpuLimit;
    private static String seleniumContainerMemoryLimit;
    private static AtomicBoolean environmentInitialised = new AtomicBoolean(false);

    static {
        readConfigurationFromEnvVariables();
    }

    private final Logger logger = LoggerFactory.getLogger(SwarmContainerClient.class.getName());
    private final GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private String nodeId;
    private Map<String, String> seleniumContainerLabels = new HashMap<>();
    private AtomicBoolean seleniumContainerLabelsChecked = new AtomicBoolean(false);

    private static void readConfigurationFromEnvVariables() {

        String cpuLimit = env.getEnvVariable(ZALENIUM_SELENIUM_CONTAINER_CPU_LIMIT);
        setSeleniumContainerCpuLimit(cpuLimit);

        String memoryLimit = env.getEnvVariable(ZALENIUM_SELENIUM_CONTAINER_MEMORY_LIMIT);
        setSeleniumContainerMemoryLimit(memoryLimit);
    }

    @VisibleForTesting
    protected static void setEnv(final Environment env) {
        SwarmContainerClient.env = env;
    }

    private static String getSeleniumContainerCpuLimit() {
        return seleniumContainerCpuLimit;
    }

    private static void setSeleniumContainerCpuLimit(String seleniumContainerCpuLimit) {
        SwarmContainerClient.seleniumContainerCpuLimit = seleniumContainerCpuLimit;
    }

    private static String getSeleniumContainerMemoryLimit() {
        return seleniumContainerMemoryLimit;
    }

    private static void setSeleniumContainerMemoryLimit(String seleniumContainerMemoryLimit) {
        SwarmContainerClient.seleniumContainerMemoryLimit = seleniumContainerMemoryLimit;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    private String getContainerId(URL remoteUrl) {
        try {
            ContainerStatus containerStatus = SwarmUtilities.getContainerByRemoteUrl(remoteUrl);
            if (containerStatus != null) {
                return containerStatus.containerId();
            }
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getContainerId(String containerName) {
        final String containerNameSearch = containerName.contains("/") ?
                containerName : String.format("/%s", containerName);

        List<Container> containerList = null;
        try {
            containerList = SwarmUtilities.getRunningAndCreatedContainers();
        } catch (DockerException | InterruptedException e) {
            logger.debug(nodeId + " Error while getting containerId", e);
            ga.trackException(e);
        }

        if (containerList != null) {
            String containerByName = containerList.stream()
                    .filter(container -> containerNameSearch.equalsIgnoreCase(container.names().get(0)))
                    .findFirst().map(Container::id).orElse(containerName);
            return containerByName != null ? containerByName : containerList.stream()
                    .filter(container -> container.names().get(0).contains(containerName))
                    .findFirst().map(Container::id).orElse(containerName);
        } else {
            return containerName;
        }
    }

    public InputStreamGroupIterator copyFiles(String containerId, String folderName) {
        // TODO: Implement behaviour
        return null;
    }

    public void stopContainer(String containerId) {
        try {
            SwarmUtilities.stopServiceByContainerId(containerId);
        } catch (DockerException | InterruptedException e) {
            logger.warn(nodeId + " Error while stopping the container", e);
            ga.trackException(e);
        }
    }

    public void executeCommand(String containerId, String[] command, boolean waitForExecution) {
        try {
            Task task = SwarmUtilities.getTaskByContainerId(containerId);
            if (task != null) {
                pullSwarmExecImage();
                startSwarmExecContainer(task, command, containerId);
            } else {
                logger.warn("Couldn't execute command on container {}", containerId);
            }
        } catch (DockerException | InterruptedException e) {
            logger.warn("Error while executing comman on container {}", containerId);
            ga.trackException(e);
        }
    }

    private void pullSwarmExecImage() {
        try {
            SwarmUtilities.pullImageIfNotPresent(SWARM_EXEC_IMAGE);
        } catch (DockerException | InterruptedException e) {
            logger.warn(nodeId + " Error while checking (and pulling) if the image is present", e);
            ga.trackException(e);
        }
    }

    private void startSwarmExecContainer(Task task, String[] command, String containerId) throws DockerException, InterruptedException {
        String taskId = task.id();
        List<String> parsedCmd = new ArrayList<>();
        String [] splittedCmd = (command[2].replace("notify 'Zalenium'", "notify, Zalenium")).split(",");
        List<String> binds = new ArrayList<>();

        binds.add("/var/run/docker.sock:/var/run/docker.sock");

        HostConfig.Builder hostConfigBuilder = HostConfig.builder()
                .autoRemove(true)
                .appendBinds(binds);

        HostConfig hostConfig = hostConfigBuilder.build();

        parsedCmd.add("task-exec");
        parsedCmd.add(taskId);
        parsedCmd.addAll(Arrays.asList(splittedCmd));

        logger.debug("Executing command: {} - on Container: {}",
                Arrays.toString(command),
                containerId);

        ContainerConfig containerConfig = ContainerConfig.builder()
                .image(SWARM_EXEC_IMAGE)
                .hostConfig(hostConfig)
                .cmd(parsedCmd)
                .build();

        SwarmUtilities.startContainer(containerConfig);
    }

    public String getLatestDownloadedImage(String imageName) {
        // TODO: verify this is handled by docker
        return imageName;
    }

    public ContainerCreationStatus createContainer(String zaleniumContainerName, String image, Map<String, String> envVars,
                                                   String nodePort) {
        // TODO: is it meaningful to add labels to identify services/containers in the swarm?

        List<String> flattenedEnvVars = envVars.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());
        final ContainerSpec containerSpec = buildContainerSpec(flattenedEnvVars, image);

        final TaskSpec taskSpec = buildTaskSpec(containerSpec);

        String noVncPort = envVars.get("NOVNC_PORT");
        final ServiceSpec serviceSpec = buildServiceSpec(taskSpec, nodePort, noVncPort);

        try {
            ServiceCreateResponse service = SwarmUtilities.createService(serviceSpec);

            TaskStatus taskStatus = waitForTaskStatus(service.id());

            if (taskStatus != null) {
                return getContainerCreationStatus(service.id(), nodePort);
            }
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }

        logger.debug("Something went wrong while creating service");
        return null;
    }

    private ContainerSpec buildContainerSpec(List<String> flattenedEnvVars, String image) {
        final Mount.Builder mountBuilder = Mount.builder()
                .source("/dev/shm")
                .target("/dev/shm")
                .type("bind");

        final ContainerSpec.Builder containerSpecBuilder = ContainerSpec.builder()
                .env(flattenedEnvVars)
                .image(image)
                .mounts(mountBuilder.build());

        loadSeleniumContainerLabels();

        if (seleniumContainerLabels.size() > 0) {
            containerSpecBuilder.labels(seleniumContainerLabels);
        }

        return containerSpecBuilder.build();
    }

    private TaskSpec buildTaskSpec(ContainerSpec containerSpec) {
        final RestartPolicy restartPolicy = RestartPolicy.builder()
                .condition("on-failure")
                .build();

        Resources.Builder resourceBuilder = Resources.builder();
        String cpuLimit = getSeleniumContainerCpuLimit();
        String memLimit = getSeleniumContainerMemoryLimit();

        if (!Strings.isNullOrEmpty(cpuLimit)) {
            resourceBuilder.nanoCpus(Long.valueOf(cpuLimit));
        }

        if (!Strings.isNullOrEmpty(memLimit)) {
            resourceBuilder.memoryBytes(Long.valueOf(memLimit));
        }

        ResourceRequirements resourceRequirements = ResourceRequirements.builder()
                .limits(resourceBuilder.build())
                .build();

        final TaskSpec.Builder taskSpecBuilder = TaskSpec.builder()
                .resources(resourceRequirements)
                .restartPolicy(restartPolicy)
                .containerSpec(containerSpec);

        if ("1".equals(env.getEnvVariable(SWARM_RUN_TESTS_ONLY_ON_WORKERS))) {
            final List<String> placementList = new ArrayList<>();
            placementList.add("node.role==worker");
            taskSpecBuilder.placement(Placement.create(placementList));
        }

        return taskSpecBuilder.build();
    }

    private ServiceSpec buildServiceSpec(TaskSpec taskSpec, String nodePort, String noVncPort) {
        final PortConfig nodePortConfig = PortConfig.builder()
                .targetPort(Integer.valueOf(nodePort))
                .publishedPort(Integer.valueOf(nodePort))
                .build();

        final PortConfig noVncPortConfig = PortConfig.builder()
                .targetPort(Integer.valueOf(noVncPort))
                .publishedPort(Integer.valueOf(noVncPort))
                .build();

        final EndpointSpec.Builder endpointSpecBuilder = EndpointSpec.builder()
                .addPort(nodePortConfig)
                .addPort(noVncPortConfig);

        final NetworkAttachmentConfig networkAttachmentConfig = NetworkAttachmentConfig.builder()
                .target(ZaleniumConfiguration.getSwarmOverlayNetwork())
                .build();

        return ServiceSpec.builder()
                .name(generateServiceName())
                .endpointSpec(endpointSpecBuilder.build())
                .networks(networkAttachmentConfig)
                .taskTemplate(taskSpec)
                .build();
    }

    private TaskStatus waitForTaskStatus(String serviceId) throws DockerException, InterruptedException {
        return waitForTaskStatus(serviceId, 0);
    }

    private TaskStatus waitForTaskStatus(String serviceId, int attempts) throws DockerException, InterruptedException {
        int attemptsLimit = 100;
        Thread.sleep(100);
        Task task = SwarmUtilities.getTaskByServiceId(serviceId);

        if (task == null && attempts < attemptsLimit) {
            return waitForTaskStatus(serviceId, attempts + 1);
        } else {
            ContainerStatus containerStatus = task.status().containerStatus();
            if (containerStatus == null) {
                return waitForTaskStatus(serviceId, attempts + 1);
            }

            logger.debug("container {} is ready", containerStatus.containerId());

            return task.status();
        }
    }

    private ContainerCreationStatus getContainerCreationStatus(String serviceId, String nodePort) throws DockerException, InterruptedException {
        Task task = SwarmUtilities.getTaskByServiceId(serviceId);

        if (task != null) {
            ContainerStatus containerStatus = task.status().containerStatus();
            if (containerStatus != null) {
                String containerId = containerStatus.containerId();
                String containerName = containerStatus.containerId();
                return new ContainerCreationStatus(true, containerName, containerId, nodePort);
            }
        }

        return null;
    }

    private String generateServiceName() {
        final String suffix = RandomStringUtils.randomAlphanumeric(6);
        return String.format("node_%s", suffix);
    }

    private void loadSeleniumContainerLabels() {
        if (!this.seleniumContainerLabelsChecked.getAndSet(true)) {
            String containerLabels = env.getStringEnvVariable("SELENIUM_CONTAINER_LABELS", "");
            if (containerLabels.length() == 0) {
                return;
            }
            try {
                for (String label : containerLabels.split(";")) {
                    String[] split = label.split("=");
                    seleniumContainerLabels.put(split[0], split[1]);
                }
            } catch (Exception e) {
                logger.warn(nodeId + " Error while retrieving the added labels for the Selenium containers.", e);
                ga.trackException(e);
            }
        }
    }

    @Override
    public void initialiseContainerEnvironment() {
        if (!environmentInitialised.getAndSet(true)) {
            // Delete any leftover services from a previous time
            deleteSwarmServices();
            // Register a shutdown hook to cleanup pods
            Runtime.getRuntime().addShutdownHook(new Thread(this::deleteSwarmServices, "SwarmContainerClient shutdown hook"));
        }
    }

    private void deleteSwarmServices() {
        // TODO: Implement functionality
    }

    @Override
    public ContainerClientRegistration registerNode(String zaleniumContainerName, URL remoteHost) {
        ContainerClientRegistration registration = new ContainerClientRegistration();

        Integer noVncPort = remoteHost.getPort() + DockeredSeleniumStarter.NO_VNC_PORT_GAP;

        String containerId = this.getContainerId(remoteHost);

        if (containerId == null) {
            logger.warn("No container id for {} - {}, can't register.", zaleniumContainerName, remoteHost.toExternalForm());
        }

        registration.setNoVncPort(noVncPort);
        registration.setContainerId(containerId);
        registration.setIpAddress(remoteHost.getHost());
        return registration;
    }

    @Override
    public String getContainerIp(String containerName) {
        String containerId = this.getContainerId(containerName);

        if (containerId == null) {
            logger.warn("Failed to get id of container: {} ", containerName);
            return null;
        }

        try {
            String swarmOverlayNetwork = ZaleniumConfiguration.getSwarmOverlayNetwork();
            Task task = SwarmUtilities.getTaskByContainerId(containerId);
            if (task != null) {
                for (NetworkAttachment networkAttachment : CollectionUtils.emptyIfNull(task.networkAttachments())) {
                    if (networkAttachment.network().spec().name().equals(swarmOverlayNetwork)) {
                        String cidrSuffix = "/\\d+$";
                        return networkAttachment.addresses().get(0).replaceAll(cidrSuffix, "");
                    }
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.debug(nodeId + " Error while getting the container IP.", e);
            ga.trackException(e);
        }

        logger.warn("Failed to get ip of container: {}", containerName);
        return null;
    }

    @Override
    public boolean isReady(ContainerCreationStatus container) {
        String containerIp = this.getContainerIp(container.getContainerName());
        if (containerIp != null) {
            try {
                URL statusUrl = new URL(String.format("http://%s:%s/wd/hub/status", containerIp, container.getNodePort()));
                String status = IOUtils.toString(statusUrl, StandardCharsets.UTF_8);
                String successMessage = "\"Node is running\"";
                if (status.contains(successMessage)) {
                    return true;
                }
            } catch (IOException e) {
                logger.debug("Error while getting node status, probably the node is still starting up...", e);
            }
        }
        return false;
    }

    @Override
    public boolean isTerminated(ContainerCreationStatus container) {
        try {
            List<String> termStates = Arrays.asList("complete", "failed", "shutdown", "rejected", "orphaned", "removed");
            String containerId = container.getContainerId();
            Task task = SwarmUtilities.getTaskByContainerId(containerId);

            if (task == null) {
                logger.info("Container {} has no corresponding task - flagging it as terminated", container);
                return true;
            } else {
                boolean isTerminated = termStates.contains(task.status().state());

                if (isTerminated) {
                    logger.info("State of Container {} is {} - flagging it as terminated",
                            container.getContainerId(),
                            task.status().state());
                }

                return isTerminated;
            }
        } catch (DockerException | InterruptedException e) {
            logger.warn("Failed to fetch container status [" + container.getContainerId() + "].", e);
            return false;
        }
    }
}


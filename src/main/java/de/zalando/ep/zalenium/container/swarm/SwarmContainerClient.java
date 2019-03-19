package de.zalando.ep.zalenium.container.swarm;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.AnsiProgressHandler;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import com.spotify.docker.client.messages.mount.Mount;
import com.spotify.docker.client.messages.swarm.*;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.proxy.DockeredSeleniumStarter;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.spotify.docker.client.DockerClient.ListContainersParam.withStatusCreated;
import static com.spotify.docker.client.DockerClient.ListContainersParam.withStatusRunning;
import static de.zalando.ep.zalenium.util.ZaleniumConfiguration.ZALENIUM_RUNNING_LOCALLY;

@SuppressWarnings("ConstantConditions")
public class SwarmContainerClient implements ContainerClient {

    private static final String DEFAULT_DOCKER_NETWORK_MODE = "default";
    private static final String DEFAULT_DOCKER_NETWORK_NAME = "bridge";
    private static final String DOCKER_NETWORK_HOST_MODE_NAME = "host";
    private static final String NODE_MOUNT_POINT = "/tmp/node";
    private static final String[] PROTECTED_NODE_MOUNT_POINTS = {
            "/var/run/docker.sock",
            "/home/seluser/videos",
            "/dev/shm"
    };
    private static final String ZALENIUM_SELENIUM_CONTAINER_CPU_LIMIT = "ZALENIUM_SELENIUM_CONTAINER_CPU_LIMIT";
    private static final String ZALENIUM_SELENIUM_CONTAINER_MEMORY_LIMIT = "ZALENIUM_SELENIUM_CONTAINER_MEMORY_LIMIT";

    private static final Environment defaultEnvironment = new Environment();
    /**
     * Number of times to attempt to create a container when the generated name is not unique.
     */
    private static final int NAME_COLLISION_RETRIES = 10;
    private static Environment env = defaultEnvironment;
    private static String seleniumContainerCpuLimit;
    private static String seleniumContainerMemoryLimit;
    private static String dockerHost;
    private static AtomicBoolean environmentInitialised = new AtomicBoolean(false);

    static {
        readConfigurationFromEnvVariables();
    }

    private final Logger logger = LoggerFactory.getLogger(SwarmContainerClient.class.getName());
    private final GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private DockerClient dockerClient = new DefaultDockerClient(dockerHost);
    private String nodeId;
    private String zaleniumNetwork;
    private List<String> zaleniumExtraHosts;
    private List<ContainerMount> mntFolders = new ArrayList<>();
    private Map<String, String> seleniumContainerLabels = new HashMap<>();
    private boolean pullSeleniumImage = false;
    private boolean isZaleniumPrivileged = true;
    private ImmutableMap<String, String> storageOpt;
    private AtomicBoolean pullSeleniumImageChecked = new AtomicBoolean(false);
    private AtomicBoolean isZaleniumPrivilegedChecked = new AtomicBoolean(false);
    private AtomicBoolean storageOptsLoaded = new AtomicBoolean(false);
    private AtomicBoolean mntFoldersAndHttpEnvVarsChecked = new AtomicBoolean(false);
    private AtomicBoolean seleniumContainerLabelsChecked = new AtomicBoolean(false);

    private static void readConfigurationFromEnvVariables() {

        String cpuLimit = env.getEnvVariable(ZALENIUM_SELENIUM_CONTAINER_CPU_LIMIT);
        setSeleniumContainerCpuLimit(cpuLimit);

        String memoryLimit = env.getEnvVariable(ZALENIUM_SELENIUM_CONTAINER_MEMORY_LIMIT);
        setSeleniumContainerMemoryLimit(memoryLimit);

        String dockerHost = env.getStringEnvVariable("DOCKER_HOST", "unix:///var/run/docker.sock");
        setDockerHost(dockerHost);
    }

    @VisibleForTesting
    protected static void setEnv(final Environment env) {
        SwarmContainerClient.env = env;
    }

    private static void setDockerHost(String dockerHost) {
        // https://github.com/spotify/docker-client/issues/946
        SwarmContainerClient.dockerHost = dockerHost.replace("tcp", "http");
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

    private static boolean isNameCollision(Exception e, String containerName) {
        return e.getMessage().contains("The container name \"" + containerName + "/\" is already in use by container ");
    }

    private static boolean hasRemainingAttempts(int collisionAttempts) {
        return collisionAttempts > 0;
    }

    @VisibleForTesting
    public void setContainerClient(final DockerClient client) {
        dockerClient = client;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    private String getContainerId(String zaleniumContainerName, URL remoteUrl) {
        try {
            List<Task> tasks = dockerClient.listTasks();
            for (Task task : tasks) {
                for (NetworkAttachment networkAttachment : task.networkAttachments()) {
                    for (String address : networkAttachment.addresses()) {
                        if (address.startsWith(remoteUrl.getHost())) {
                            return task.status().containerStatus().containerId();
                        }
                    }
                }
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
            containerList = dockerClient.listContainers(withStatusRunning(), withStatusCreated());
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

    public InputStream copyFiles(String containerId, String folderName) {
        try {
            return dockerClient.archiveContainer(containerId, folderName);
        } catch (DockerException | InterruptedException e) {
            logger.warn(nodeId + " Something happened while copying the folder " + folderName + ", " +
                    "most of the time it is an issue while closing the input/output stream, which is usually OK.", e);
        }
        return null;
    }

    public void stopContainer(String containerId) {
        try {
            List<Task> tasks = dockerClient.listTasks();
            for (Task task : tasks) {
                ContainerStatus containerStatus = task.status().containerStatus();
                if (containerStatus != null && containerStatus.containerId().equals(containerId)) {
                    String serviceId = task.serviceId();
                    List<Service> services = dockerClient.listServices();
                    if (services.stream().anyMatch(service -> service.id().equals(serviceId))) {
                        // TODO: This should STOP the container and not remove the service.
                        //       After this command there is nothing left from the container
                        //       but it still is registered inside the zalenium logik
                        dockerClient.removeService(serviceId);


                        // TODO: Replicas reduzieren?!?
                        // Service service = dockerClient.inspectService(serviceId);
                        // ServiceSpec serviceSpec = service.spec();
                        // dockerClient.updateService(serviceId, 1L, );
                    }
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.warn(nodeId + " Error while stopping the container", e);
            ga.trackException(e);
        }
    }

    public void executeCommand(String containerId, String[] command, boolean waitForExecution) {
        try {
            List<Task> tasks = dockerClient.listTasks();
            for (Task task : tasks) {
                ContainerStatus containerStatus = task.status().containerStatus();
                if (containerStatus != null && containerStatus.containerId().equals(containerId)) {
                    String taskId = task.id();
                    String image = "datagridsys/skopos-plugin-swarm-exec:latest";

                    List<String> binds = new ArrayList<>();
                    binds.add("/var/run/docker.sock:/var/run/docker.sock");

                    HostConfig.Builder hostConfigBuilder = HostConfig.builder()
                            .autoRemove(true)
                            .appendBinds(binds);

                    HostConfig hostConfig = hostConfigBuilder.build();

                    command[0] = "task-exec";
                    command[1] = taskId;

                    logger.debug(String.format("Container ID: %s; Task ID: %s; Command: %s",
                            containerId,
                            taskId,
                            Arrays.toString(command)));

                    dockerClient.pull(image, new AnsiProgressHandler());

                    ContainerConfig containerConfig = ContainerConfig.builder()
                            .image(image)
                            .hostConfig(hostConfig)
                            .cmd(command)
                            .build();

                    ContainerCreation containerCreation = dockerClient.createContainer(containerConfig);

                    dockerClient.startContainer(containerCreation.id());
                }
            }
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String getLatestDownloadedImage(String imageName) {
        List<Image> images;
        try {
            images = dockerClient.listImages(DockerClient.ListImagesParam.byName(imageName));
            if (images.isEmpty()) {
                logger.error(nodeId + " A downloaded docker-selenium image was not found!");
                return imageName;
            }
            for (int i = images.size() - 1; i >= 0; i--) {
                if (images.get(i).repoTags() == null) {
                    images.remove(i);
                }
            }
            images.sort((o1, o2) -> o2.created().compareTo(o1.created()));
            return images.get(0).repoTags().get(0);
        } catch (DockerException | InterruptedException e) {
            logger.warn(nodeId + " Error while executing the command", e);
            ga.trackException(e);
        }
        return imageName;
    }

    public ContainerCreationStatus createContainer(String zaleniumContainerName, String image, Map<String, String> envVars,
                                                   String nodePort) {
        loadMountedFolders(zaleniumContainerName);
        loadSeleniumContainerLabels();
        loadPullSeleniumImageFlag();
        loadIsZaleniumPrivileged(zaleniumContainerName);
        loadStorageOpts(zaleniumContainerName);

        List<String> flattenedEnvVars = envVars.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());

        final ContainerSpec containerSpec = buildContainerSpec(flattenedEnvVars, image);
        final TaskSpec taskSpec = buildTaskSpec(containerSpec);
        String noVncPort = envVars.get("NOVNC_PORT");
        final ServiceSpec serviceSpec = buildServiceSpec(taskSpec, nodePort, noVncPort);

        try {
            ServiceCreateResponse service = dockerClient.createService(serviceSpec);

            TaskStatus taskStatus = waitForTaskStatus(service.id());

            if(taskStatus != null) {
                return getContainerCreationStatus(service.id(), nodePort);
            }
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }

        try {
            if (pullSeleniumImage) {
                List<Image> images = dockerClient.listImages(DockerClient.ListImagesParam.byName(image));
                if (images.size() == 0) {
                    // If the image has no tag, we add latest, otherwise we end up pulling all the images with that name.
                    String imageToPull = image.lastIndexOf(':') > 0 ? image : image.concat(":latest");
                    dockerClient.pull(imageToPull, new AnsiProgressHandler());
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.warn(nodeId + " Error while checking (and pulling) if the image is present", e);
            ga.trackException(e);
        }

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

        if (seleniumContainerLabels.size() > 0) {
            containerSpecBuilder.labels(seleniumContainerLabels);
        }

        return containerSpecBuilder.build();
    }

    private TaskSpec buildTaskSpec(ContainerSpec containerSpec) {
        final RestartPolicy restartPolicy = RestartPolicy.builder()
                .condition("none")
                .build();

        final List<String> placementList = new ArrayList<>();

        placementList.add("node.role==worker");

        final Placement placement = Placement.create(placementList);

        final TaskSpec.Builder taskSpecBuilder = TaskSpec.builder()
                .restartPolicy(restartPolicy)
                .placement(placement)
                .containerSpec(containerSpec);

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
        TaskStatus taskStatus = null;
        String serviceName = dockerClient.inspectService(serviceId).spec().name();
        Task.Criteria criteria = Task.Criteria.builder().serviceName(serviceName).build();
        List<Task> tasks = dockerClient.listTasks(criteria);
        Task task = tasks.get(0);

        String taskId = task == null ? null : task.id();


        if (task == null && attempts < attemptsLimit) {
            return waitForTaskStatus(serviceId, attempts + 1);
        } else {
            ContainerStatus containerStatus = task.status().containerStatus();
            if (containerStatus == null) {
                return waitForTaskStatus(serviceId, attempts + 1);
            }
            return task.status();
        }
    }

    private ContainerCreationStatus getContainerCreationStatus (String serviceId, String nodePort) throws DockerException, InterruptedException {
        List<Task> tasks = dockerClient.listTasks();
        for (Task task : tasks) {
            if (task.serviceId().equals(serviceId)) {
                ContainerStatus containerStatus = task.status().containerStatus();
                if (containerStatus != null) {
                    String containerId = containerStatus.containerId();
                    String containerName =  containerStatus.containerId();
                    return new ContainerCreationStatus(true, containerName, containerId, nodePort);
                }
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

    private void loadPullSeleniumImageFlag() {
        if (!this.pullSeleniumImageChecked.getAndSet(true)) {
            pullSeleniumImage = env.getBooleanEnvVariable("PULL_SELENIUM_IMAGE", false);
        }
    }

    private void loadIsZaleniumPrivileged(String zaleniumContainerName) {
        if (!this.isZaleniumPrivilegedChecked.getAndSet(true)) {
            String containerId = getContainerId(zaleniumContainerName);
            if (containerId == null) {
                return;
            }

            ContainerInfo containerInfo;

            try {
                containerInfo = dockerClient.inspectContainer(containerId);
                isZaleniumPrivileged = containerInfo.hostConfig().privileged();
            } catch (DockerException | InterruptedException e) {
                logger.warn(nodeId + " Error while getting value to check if Zalenium is running in privileged mode.", e);
                ga.trackException(e);
            }
        }
    }

    private void loadStorageOpts(String zaleniumContainerName) {
        if (!this.storageOptsLoaded.getAndSet(true)) {
            String containerId = getContainerId(zaleniumContainerName);
            if (containerId == null) {
                return;
            }

            ContainerInfo containerInfo;

            try {
                containerInfo = dockerClient.inspectContainer(containerId);
                storageOpt = containerInfo.hostConfig().storageOpt();
            } catch (DockerException | InterruptedException e) {
                logger.warn(nodeId + " Error while getting value to use passed storageOpts.", e);
                ga.trackException(e);
            }
        }
    }

    private void loadMountedFolders(String zaleniumContainerName) {
        if (!this.mntFoldersAndHttpEnvVarsChecked.get()) {
            String containerId = getContainerId(zaleniumContainerName);
            if (containerId == null) {
                return;
            }

            ContainerInfo containerInfo = null;

            try {
                containerInfo = dockerClient.inspectContainer(containerId);
            } catch (DockerException | InterruptedException e) {
                logger.warn(nodeId + " Error while getting mounted folders and env vars.", e);
                ga.trackException(e);
            }

            loadMountedFolders(containerInfo);
        }
    }

    private synchronized void loadMountedFolders(ContainerInfo containerInfo) {
        if (!this.mntFoldersAndHttpEnvVarsChecked.getAndSet(true)) {

            for (ContainerMount containerMount : containerInfo.mounts()) {
                if (containerMount.destination().startsWith(NODE_MOUNT_POINT)) {
                    this.mntFolders.add(containerMount);
                }
            }
        }
    }

    @Override
    public void initialiseContainerEnvironment() {
        if (!environmentInitialised.getAndSet(true)) {
            // Delete any leftover containers from a previous time
            deleteSeleniumContainers();
            // Register a shutdown hook to cleanup pods
            Runtime.getRuntime().addShutdownHook(new Thread(this::deleteSeleniumContainers, "DockerContainerClient shutdown hook"));
        }
    }

    private void deleteSeleniumContainers() {
        logger.info("About to clean up any left over DockerSelenium containers created by Zalenium");
        String image = DockeredSeleniumStarter.getDockerSeleniumImageName();
        String zaleniumContainerName = DockeredSeleniumStarter.getContainerName();
        try {
            List<Container> containerList = dockerClient.listContainers(withStatusRunning(), withStatusCreated())
                    .stream().filter(container -> container.image().contains(image)
                            && container.names().stream().anyMatch(name -> name.contains(zaleniumContainerName)))
                    .collect(Collectors.toList());
            containerList.stream()
                    .parallel()
                    .forEach(container -> stopContainer(container.id()));
        } catch (Exception e) {
            logger.warn(nodeId + " Error while deleting existing DockerSelenium containers", e);
            ga.trackException(e);
        }
    }

    @Override
    public ContainerClientRegistration registerNode(String zaleniumContainerName, URL remoteHost) {
        ContainerClientRegistration registration = new ContainerClientRegistration();

        Integer noVncPort = remoteHost.getPort() + DockeredSeleniumStarter.NO_VNC_PORT_GAP;

        String containerId = this.getContainerId(zaleniumContainerName, remoteHost);

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
            return null;
        }
        try {
            List<Task> tasks = dockerClient.listTasks();
            String swarmOverlayNetwork = ZaleniumConfiguration.getSwarmOverlayNetwork();
            for (Task task : tasks) {
                ContainerStatus containerStatus = task.status().containerStatus();
                if (containerStatus != null) {
                    if (containerStatus.containerId().equals(containerId)) {
                        for (NetworkAttachment networkAttachment : task.networkAttachments()) {
                            if (networkAttachment.network().spec().name().equals(swarmOverlayNetwork)) {
                                String cidrSuffix = "/\\d+$";
                                return networkAttachment.addresses().get(0).replaceAll(cidrSuffix, "");
                            }
                        }
                    }
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.debug(nodeId + " Error while getting the container IP.", e);
            ga.trackException(e);
        }
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
        List<String> termStates = Arrays.asList("complete", "failed", "shutdown", "rejected", "orphaned", "removed");
        String containerId = container.getContainerId();

        try {
            List<Task> tasks = dockerClient.listTasks();
            for (Task task : tasks) {
                TaskStatus taskStatus = task.status();
                ContainerStatus containerStatus = taskStatus.containerStatus();
                if (containerStatus != null && containerStatus.containerId().equals(containerId)) {
                    String state = taskStatus.state();
                    return termStates.contains(state);
                }
            }
            return false;
        } catch (DockerException | InterruptedException e) {
            logger.warn("Failed to fetch container status [" + container + "].", e);
            return false;
        }
    }
}


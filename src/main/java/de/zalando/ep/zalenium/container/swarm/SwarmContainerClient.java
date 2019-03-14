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
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
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
                    .findFirst().map(Container::id).orElse(null);
            return containerByName != null ? containerByName : containerList.stream()
                    .filter(container -> container.names().get(0).contains(containerName))
                    .findFirst().map(Container::id).orElse(null);
        } else {
            return null;
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
                if (task.status().containerStatus().containerId().equals(containerId)) {
                    String serviceId = task.serviceId();
                    dockerClient.removeService(serviceId);
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.warn(nodeId + " Error while stopping the container", e);
            ga.trackException(e);
        }
    }

    // TODO: Work In Progress => Must be implemented correctly
    public void executeCommand(String containerId, String[] command, boolean waitForExecution) {
        String swarmNodeIp = null;
        try {
            List<Task> tasks = dockerClient.listTasks();
            for (Task task : tasks) {
                if (task.status().containerStatus().containerId().equals(containerId)) {
                    List<Node> nodes = dockerClient.listNodes();
                    for (Node node :nodes) {
                        if (node.id().equals(task.nodeId())) {
                            swarmNodeIp = node.status().addr();
                        }
                    }
                }
            }
        } catch (DockerException | InterruptedException | NullPointerException e) {
            logger.debug(nodeId + " Error while executing the command", e);
            ga.trackException(e);
        }

        if (swarmNodeIp != null) {
            execCommandOnRemote(swarmNodeIp, containerId, command);
        }
    }

    // TODO: Work In Progress => Must be implemented correctly
    private void execCommandOnRemote (String ip, String containerId, String[] command) {
        SSHClient ssh = new SSHClient();
        try {
            ssh.connect(ip);
            ssh.authPublickey("id_rsa");
            Session session = ssh.startSession();
            Session.Command cmd = session.exec("docker exec -ti " + containerId + " sh -c notify 'Zalenium', 'TEST COMPLETED', --icon=/home/seluser/images/completed.png");
            System.out.println(cmd.toString());
            session.close();
            ssh.disconnect();
        } catch (IOException e) {
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

        final TaskSpec.Builder taskSpecBuilder = TaskSpec.builder()
                .restartPolicy(restartPolicy)
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
        int attemptsLimit = 50;
        Thread.sleep(2000);
        TaskStatus taskStatus = null;
        List<Task> tasks = dockerClient.listTasks();

        for (Task task : tasks) {
            String currentId = task.serviceId();
            if (currentId.equals(serviceId) && task.status().state().equals("running")) {
                taskStatus = task.status();
            }
        }

        if (taskStatus == null && attempts < attemptsLimit) {
            return waitForTaskStatus(serviceId, attempts + 1);
        } else {
            return taskStatus;
        }
    }

    private ContainerCreationStatus getContainerCreationStatus (String serviceId, String nodePort) throws DockerException, InterruptedException {
        List<Task> tasks = dockerClient.listTasks();
        for (Task task : tasks) {
            if (task.serviceId().equals(serviceId)) {
                ContainerStatus containerStatus = task.status().containerStatus();
                String containerId = containerStatus.containerId();
                String containerName =  containerStatus.containerId();
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

    private List<String> generateMountedFolderBinds() {
        List<String> result = new ArrayList<>();

        this.mntFolders.stream().filter(mount -> mount.destination().startsWith(NODE_MOUNT_POINT)).forEach(
                containerMount -> {
                    String destination = containerMount.destination().substring(NODE_MOUNT_POINT.length());

                    if (Arrays.stream(PROTECTED_NODE_MOUNT_POINTS).anyMatch(item -> item.equalsIgnoreCase(destination))) {
                        throw new IllegalArgumentException("The following points may not be mounted via node mounting: "
                                + String.join(",", PROTECTED_NODE_MOUNT_POINTS));
                    }
                    String mountedBind = String.format("%s:%s", containerMount.source(), destination);
                    result.add(mountedBind);
                }
        );

        return result;
    }

    private synchronized List<String> getContainerExtraHosts(String zaleniumContainerName) {
        if (zaleniumExtraHosts != null) {
            return zaleniumExtraHosts;
        }
        String containerId = getContainerId(zaleniumContainerName);
        ContainerInfo containerInfo;
        try {
            containerInfo = dockerClient.inspectContainer(containerId);
            zaleniumExtraHosts = containerInfo.hostConfig().extraHosts();
        } catch (DockerException | InterruptedException | NullPointerException e) {
            logger.debug(nodeId + " Error while getting Zalenium extra hosts.", e);
            ga.trackException(e);
        }
        return Optional.ofNullable(zaleniumExtraHosts).orElse(new ArrayList<>());
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
            ContainerInfo containerInfo = dockerClient.inspectContainer(containerId);
            if (containerInfo.networkSettings().ipAddress().trim().isEmpty()) {
                ImmutableMap<String, AttachedNetwork> networks = containerInfo.networkSettings().networks();
                return networks.entrySet().stream().findFirst().get().getValue().ipAddress();
            }
            return containerInfo.networkSettings().ipAddress();
        } catch (DockerException | InterruptedException e) {
            logger.debug(nodeId + " Error while getting the container IP.", e);
            ga.trackException(e);
        }
        return null;
    }

    @Override
    public boolean isReady(ContainerCreationStatus container) {
        String containerIp = this.getContainerIp(container.getContainerName());
        if (ZALENIUM_RUNNING_LOCALLY) {
            containerIp = "localhost";
        }
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
                boolean isContainer = taskStatus.containerStatus().containerId().equals(containerId);
                String state = taskStatus.state();
                if (isContainer && termStates.contains(state)) {
                    return true;
                }
            }
            return false;
        } catch (DockerException | InterruptedException e) {
            logger.warn("Failed to fetch container status [" + container + "].", e);
            return false;
        }
    }
}


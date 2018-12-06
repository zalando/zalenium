package de.zalando.ep.zalenium.container.kubernetes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.util.Environment;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HostAlias;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import okhttp3.Response;

public class KubernetesContainerClient implements ContainerClient {

    private static final String[] PROTECTED_NODE_MOUNT_POINTS = {
            "/home/seluser/videos",
            "/dev/shm"
    };

    private static final Logger logger = LoggerFactory.getLogger(KubernetesContainerClient.class.getName());

    private KubernetesClient client;

    private String zaleniumAppName;

    private Pod zaleniumPod;

    private Map<String, String> createdByZaleniumMap;
    private Map<String, String> appLabelMap;

    private Map<VolumeMount, Volume> mountedSharedFoldersMap = new HashMap<>();
    private List<HostAlias> hostAliases = new ArrayList<>();
    private Map<String, String> nodeSelector = new HashMap<>();
    private List<Toleration> tolerations = new ArrayList<>();
    private String imagePullPolicy;

    private final Map<String, Quantity> seleniumPodLimits = new HashMap<>();
    private final Map<String, Quantity> seleniumPodRequests = new HashMap<>();

    private final Environment environment;

    private final Function<PodConfiguration, DoneablePod> createDoneablePod;

    public KubernetesContainerClient(Environment environment,
                                     Function<PodConfiguration, DoneablePod> createDoneablePod,
                                     KubernetesClient client) {
        logger.info("Initialising Kubernetes support");

        this.environment = environment;
        this.createDoneablePod = createDoneablePod;
        try {
            this.client = client;

            // Lookup our current hostname, this lets us lookup ourselves via the kubernetes api
            String hostname = findHostname();

            zaleniumPod = client.pods().withName(hostname).get();

            String appName = zaleniumPod.getMetadata().getLabels().get("app");

            appLabelMap = new HashMap<>();
            appLabelMap.put("app", appName);

            createdByZaleniumMap = new HashMap<>();
            createdByZaleniumMap.put("createdBy", appName);
            zaleniumAppName = appName;

            discoverFolderMounts();
            discoverHostAliases();
            discoverNodeSelector();
            discoverTolerations();

            buildResourceMaps();

            logger.info(String.format(
                    "Kubernetes support initialised.\n"
                            + "\tPod name: %s\n"
                            + "\tapp label: %s\n"
                            + "\tzalenium service name: %s\n"
                            + "\tSelenium Pod Resource Limits: %s\n"
                            + "\tSelenium Pod Resource Requests: %s",
                    hostname, appName, zaleniumAppName,
                            seleniumPodLimits.toString(), seleniumPodRequests.toString()));
        } catch (Exception e) {
            logger.warn("Error initialising Kubernetes support.", e);
        }
    }

    private void buildResourceMaps() {
        for (Resources resource : Resources.values()) {
            String envValue = environment.getStringEnvVariable(resource.getEnvVar(), null);
            if (StringUtils.isNotBlank(envValue)) {
                Map<String, Quantity> resourceMap = null;
                switch (resource.getResourceType()) {
                    case REQUEST:
                        resourceMap = seleniumPodRequests;
                        break;
                    case LIMIT:
                        resourceMap = seleniumPodLimits;
                        break;
                    default:
                        break;
                }
                if (resourceMap != null) {
                    Quantity quantity = new Quantity(envValue);
                    resourceMap.put(resource.getRequestType(), quantity);
                }
            }
        }
        // Default to imagePullPolicy: Always if ENV variable "ZALENIUM_KUBERNETES_IMAGE_PULL_POLICY" is not provided
        imagePullPolicy = environment.getStringEnvVariable("ZALENIUM_KUBERNETES_IMAGE_PULL_POLICY", ImagePullPolicyType.Always.name());
    }

    private void discoverHostAliases() {
        List<HostAlias> configuredHostAliases = zaleniumPod.getSpec().getHostAliases();
        if (!configuredHostAliases.isEmpty()) {
            hostAliases = configuredHostAliases;
        }
    }

    private void discoverNodeSelector() {
        final Map<String, String> configuredNodeSelector = zaleniumPod.getSpec().getNodeSelector();
        if (configuredNodeSelector != null && !configuredNodeSelector.isEmpty()) {
            nodeSelector = configuredNodeSelector;
        }
    }

    private void discoverTolerations() {
        final List<Toleration> configuredTolerations = zaleniumPod.getSpec().getTolerations();
        if (configuredTolerations != null && !configuredTolerations.isEmpty()) {
            tolerations = configuredTolerations;
        }
    }

    private void discoverFolderMounts() {
        List<VolumeMount> volumeMounts = zaleniumPod.getSpec().getContainers().get(0).getVolumeMounts();

        List<VolumeMount> validMounts = new ArrayList<>();
        volumeMounts.stream()
                .filter(volumeMount -> !Arrays.asList(PROTECTED_NODE_MOUNT_POINTS).contains(volumeMount.getMountPath()))
                .forEach(validMounts::add);

        // Look through the volume mounts to see if the shared folder is mounted
        if (!validMounts.isEmpty()) {
            List<Volume> volumes = zaleniumPod.getSpec().getVolumes();
            for (VolumeMount validMount : validMounts) {
                volumes.stream()
                        .filter(volume -> validMount.getName().equalsIgnoreCase(volume.getName()))
                        .findFirst()
                        .ifPresent(volume -> mountedSharedFoldersMap.put(validMount, volume));
            }
        }
    }

    private String findHostname() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            hostname = null;
        }

        return hostname;
    }

    @Override
    public void setNodeId(String nodeId) {
        // We don't care about the nodeId, as it's essentially the same as the containerId, which is passed in where necessary.
    }

    /**
     * Copy some files by executing a tar command to the stdout and return the InputStream that contains the tar
     * contents.
     * <p>
     * Unfortunately due to the fact that any error handling happens on another thread, if the tar command fails the
     * InputStream will simply be empty and it will close. It won't propagate an Exception to the reader of the
     * InputStream.
     */
    @Override
    public InputStream copyFiles(String containerId, String folderName) {

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String[] command = new String[] { "tar", "-C", folderName, "-c", "." };
        CopyFilesExecListener listener = new CopyFilesExecListener(stderr, command, containerId);
        ExecWatch exec = client.pods().withName(containerId).redirectingOutput().writingError(stderr).usingListener(listener).exec(command);

        // FIXME: This is a bit dodgy, but we need the listener to be able to close the ExecWatch in failure conditions,
        // because it doesn't cleanup properly and deadlocks.
        // Needs bugs fixed inside kubernetes-client.
        listener.setExecWatch(exec);

        // When zalenium is under high load sometimes the stdout isn't connected by the time we try to read from it.
        // Let's wait until it is connected before proceeding.
        listener.waitForInputStreamToConnect();

        return exec.getOutput();
    }

    @Override
    public void stopContainer(String containerId) {
        client.pods().withName(containerId).delete();
    }

    @Override
    public void executeCommand(String containerId, String[] command, boolean waitForExecution) {
        final CountDownLatch latch = new CountDownLatch(1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        logger.debug(String.format("%s %s", containerId, Arrays.toString(command)));
        ExecWatch exec = client.pods().withName(containerId).writingOutput(baos).writingError(baos).usingListener(new ExecListener() {

            @Override
            public void onOpen(Response response) {
            }

            @Override
            public void onFailure(Throwable t,
                                  Response response) {
                logger.error(String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)), t);
                latch.countDown();
            }

            @Override
            public void onClose(int code,
                                String reason) {
                latch.countDown();
            }
        }).exec(command);

        Supplier<Void> waitForResultsAndCleanup = () -> {

            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.error(String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)), e);
            } finally {
                exec.close();
            }

            logger.debug(String.format("%s completed %s", containerId, Arrays.toString(command)));
            logger.debug(String.format("%s %s", containerId, baos.toString()));

            return null;
        };
        if (waitForExecution) {
            // If we're going to wait, let's use the same thread
            waitForResultsAndCleanup.get();
        }
        else {
            // Let the common ForkJoinPool handle waiting for the results, since we don't care when it finishes.
            CompletableFuture.supplyAsync(waitForResultsAndCleanup);
        }
    }

    @Override
    public String getLatestDownloadedImage(String imageName) {
        // Nothing to do here, this is managed by the ImagePullPolicy when creating a container.
        // Currently the kubernetes API can't manage images, the OpenShift API has some extra hooks though, which we could potential use.
        return imageName;
    }

    @Override
    public ContainerCreationStatus createContainer(String zaleniumContainerName, String image, Map<String, String> envVars,
                               String nodePort) {
        String containerIdPrefix = String.format("%s-%s-", zaleniumAppName, nodePort);

        // Convert the environment variables into the Kubernetes format.
        List<EnvVar> flattenedEnvVars = envVars.entrySet().stream()
                                            .map(e -> new EnvVar(e.getKey(), e.getValue(), null))
                                            .collect(Collectors.toList());

        Map<String, String> podSelector = new HashMap<>();

        PodConfiguration config = new PodConfiguration();
        config.setNodePort(nodePort);
        config.setClient(client);
        config.setContainerIdPrefix(containerIdPrefix);
        config.setImage(image);
        config.setEnvVars(flattenedEnvVars);
        Map<String, String> labels = new HashMap<>();
        labels.putAll(createdByZaleniumMap);
        labels.putAll(appLabelMap);
        labels.putAll(podSelector);
        config.setLabels(labels);
        config.setImagePullPolicy(imagePullPolicy);
        config.setMountedSharedFoldersMap(mountedSharedFoldersMap);
        config.setHostAliases(hostAliases);
        config.setNodeSelector(nodeSelector);
        config.setTolerations(tolerations);
        config.setPodLimits(seleniumPodLimits);
        config.setPodRequests(seleniumPodRequests);

        DoneablePod doneablePod = createDoneablePod.apply(config);

        // Create the container
        Pod createdPod = doneablePod.done();
        String containerName = createdPod.getMetadata() == null ? containerIdPrefix : createdPod.getMetadata().getName();
        return new ContainerCreationStatus(true, containerName, containerName, nodePort);
    }

    @Override
    public void initialiseContainerEnvironment() {
        // Delete any leftover pods from a previous time
        deleteSeleniumPods();

        // Register a shutdown hook to cleanup pods
        Runtime.getRuntime().addShutdownHook(new Thread(this::deleteSeleniumPods, "KubernetesContainerClient shutdown hook"));
    }

    @Override
    public String getContainerIp(String containerName) {
        Pod pod = client.pods().withName(containerName).get();
        if (pod != null) {
            String podIP = pod.getStatus().getPodIP();
            logger.debug(String.format("Pod %s, IP -> %s", containerName, podIP));
            return podIP;
        }
        else {
            return null;
        }
    }

    public boolean isReady(ContainerCreationStatus container) {
        Pod pod = client.pods().withName(container.getContainerName()).get();
        if (pod == null) {
            return false;
        }
        else {
            return pod.getStatus().getConditions().stream()
                    .filter(condition -> condition.getType().equals("Ready"))
                    .map(condition -> condition.getStatus().equals("True"))
                    .findFirst()
                    .orElse(false);
        }
    }

    public boolean isTerminated(ContainerCreationStatus container) {
        Pod pod = client.pods().withName(container.getContainerName()).get();
        if (pod == null) {
            logger.info("Container {} has no pod - terminal.", container);
            return true;
        }
        else {
              List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
              Optional<ContainerStateTerminated> terminated = containerStatuses.stream()
                      .flatMap(status -> Optional.ofNullable(status.getState()).map(Stream::of).orElse(Stream.empty()))
                      .flatMap(state -> Optional.ofNullable(state.getTerminated()).map(Stream::of).orElse(Stream.empty()))
                      .findFirst();

              terminated.ifPresent(state -> logger.info("Container {} is {} - terminal.", container, state));

              return terminated.isPresent();
        }
    }

    private void deleteSeleniumPods() {
        logger.info("About to clean up any left over selenium pods created by Zalenium");
        client.pods().withLabels(createdByZaleniumMap).delete();
    }

    @Override
    public ContainerClientRegistration registerNode(String zaleniumContainerName, URL remoteHost) {
        String podIpAddress = remoteHost.getHost();

        // The only way to lookup a pod name by IP address is by looking at all pods in the namespace it seems.
        PodList list = client.pods().withLabels(createdByZaleniumMap).list();

        String containerId = null;
        Pod currentPod = null;
        for (Pod pod : list.getItems()) {

            if (podIpAddress.equals(pod.getStatus().getPodIP())) {
                containerId = pod.getMetadata().getName();
                currentPod = pod;
                break;
            }
        }

        if (containerId == null) {
            throw new IllegalStateException("Unable to locate pod by ip address, registration will fail");
        }
        ContainerClientRegistration registration = new ContainerClientRegistration();

        List<EnvVar> podEnvironmentVariables = currentPod.getSpec().getContainers().get(0).getEnv();
        Optional<EnvVar> noVncPort = podEnvironmentVariables.stream().filter(env -> "NOVNC_PORT".equals(env.getName())).findFirst();

        if (noVncPort.isPresent()) {
            Integer noVncPortInt = Integer.decode(noVncPort.get().getValue());

            registration.setNoVncPort(noVncPortInt);
        }
        else {
            logger.warn(String.format("%s Couldn't find NOVNC_PORT, live preview will not work.", containerId));
        }

        registration.setIpAddress(currentPod.getStatus().getPodIP());
        registration.setContainerId(containerId);

        return registration;
    }

    @SuppressWarnings("WeakerAccess")
    private final class CopyFilesExecListener implements ExecListener {
        private AtomicBoolean closedResource = new AtomicBoolean(false);
        private ExecWatch execWatch;
        private String containerId;
        private ByteArrayOutputStream stderr;
        private String[] command;
        private final CountDownLatch openLatch = new CountDownLatch(1);

        public CopyFilesExecListener(ByteArrayOutputStream stderr, String[] command, String containerId) {
            super();
            this.stderr = stderr;
            this.command = command;
            this.containerId = containerId;
        }

        public void setExecWatch(ExecWatch execWatch) {
            this.execWatch = execWatch;
        }

        @Override
        public void onOpen(Response response) {
            openLatch.countDown();
        }

        @Override
        public void onFailure(Throwable t,
                              Response response) {
            logger.error(String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)), t);
        }

        @Override
        public void onClose(int code,
                            String reason) {


            // Dirty hack to workaround the fact that ExecWatch doesn't automatically close any resources
            boolean isClosed = closedResource.getAndSet(true);
            boolean hasErrors = stderr.size() > 0;
            if (!isClosed && hasErrors) {
                logger.error(String.format("%s Copy files command failed with:\n\tcommand: %s\n\t stderr:\n%s",
                                        containerId,
                                        Arrays.toString(command),
                                        stderr.toString()));
                this.execWatch.close();
            }
        }

        public void waitForInputStreamToConnect() {
            try {
                this.openLatch.await();
            }
            catch (InterruptedException e) {
                logger.error( String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)), e);
            }
        }
    }

    private enum Resources {

        CPU_REQUEST(ResourceType.REQUEST, "cpu", "ZALENIUM_KUBERNETES_CPU_REQUEST"),
        CPU_LIMIT(ResourceType.LIMIT, "cpu", "ZALENIUM_KUBERNETES_CPU_LIMIT"),
        MEMORY_REQUEST(ResourceType.REQUEST, "memory", "ZALENIUM_KUBERNETES_MEMORY_REQUEST"),
        MEMORY_LIMIT(ResourceType.LIMIT, "memory", "ZALENIUM_KUBERNETES_MEMORY_LIMIT");

        private ResourceType resourceType;
        private String requestType;
        private String envVar;

        Resources(ResourceType resourceType, String requestType, String envVar) {
            this.resourceType = resourceType;
            this.requestType = requestType;
            this.envVar = envVar;
        }

        public String getRequestType() {
            return requestType;
        }

        public ResourceType getResourceType() {
            return resourceType;
        }

        public String getEnvVar() {
            return envVar;
        }
    }

    private enum ResourceType {
        REQUEST, LIMIT
    }

    private enum ImagePullPolicyType {
        Always, IfNotPresent
    }

    public static DoneablePod createDoneablePodDefaultImpl(PodConfiguration config) {
        DoneablePod doneablePod = config.getClient().pods()
                .createNew()
                .withNewMetadata()
                    .withGenerateName(config.getContainerIdPrefix())
                    .addToLabels(config.getLabels())
                .endMetadata()
                .withNewSpec()
                    .withNodeSelector(config.getNodeSelector())
                    .withTolerations(config.getTolerations())
                    // Add a memory volume that we can use for /dev/shm
                    .addNewVolume()
                        .withName("dshm")
                        .withNewEmptyDir()
                            .withMedium("Memory")
                        .endEmptyDir()
                    .endVolume()
                    .addNewContainer()
                        .withName("selenium-node")
                        .withImage(config.getImage())
                        .withImagePullPolicy(config.getImagePullPolicy())
                        .addAllToEnv(config.getEnvVars())
                        .addNewVolumeMount()
                            .withName("dshm")
                            .withMountPath("/dev/shm")
                        .endVolumeMount()
                        .withNewResources()
                            .addToLimits(config.getPodLimits())
                            .addToRequests(config.getPodRequests())
                        .endResources()
                        // Add a readiness health check so that we can know when the selenium pod is ready to accept requests
                        // so then we can initiate a registration.
                        .withNewReadinessProbe()
                            .withNewExec()
                                .addToCommand(new String[] {"/bin/sh", "-c", "http_proxy=\"\" curl -s http://`getent hosts ${HOSTNAME} | awk '{ print $1 }'`:"
                                        + config.getNodePort() + "/wd/hub/status | jq .value.ready | grep true"})
                            .endExec()
                            .withInitialDelaySeconds(5)
                            .withFailureThreshold(60)
                            .withPeriodSeconds(1)
                            .withTimeoutSeconds(5)
                            .withSuccessThreshold(1)
                        .endReadinessProbe()
                    .endContainer()
                    .withRestartPolicy("Never")
                .endSpec();

        // Add the shared folders if available
        for (Map.Entry<VolumeMount, Volume> entry : config.getMountedSharedFoldersMap().entrySet()) {
            doneablePod = doneablePod
                    .editSpec()
                        .addNewVolumeLike(entry.getValue())
                    .and()
                        .editFirstContainer()
                            .addNewVolumeMountLike(entry.getKey())
                            .endVolumeMount()
                        .endContainer()
                    .endSpec();
        }

        // Add configured host aliases, if any
        for (HostAlias hostAlias : config.getHostAliases()) {
            doneablePod = doneablePod
                    .editSpec()
                        .addNewHostAliasLike(hostAlias)
                        .endHostAlias()
                    .endSpec();
        }

        return doneablePod;
    }
}

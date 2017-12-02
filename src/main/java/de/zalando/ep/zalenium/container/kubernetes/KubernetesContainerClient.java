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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.util.Environment;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HostAlias;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.Response;

public class KubernetesContainerClient implements ContainerClient {

    private static final String[] PROTECTED_NODE_MOUNT_POINTS = {
            "/home/seluser/videos",
            "/dev/shm"
    };

    private static final Logger logger = Logger.getLogger(KubernetesContainerClient.class.getName());

    private KubernetesClient client;
    @SuppressWarnings("unused")
    private OpenShiftClient oClient;

    private String zaleniumAppName;

    private Pod zaleniumPod;

    private Map<String, String> createdByZaleniumMap;
    private Map<String, String> appLabelMap;

    private Map<VolumeMount, Volume> mountedSharedFoldersMap = new HashMap<>();
    private List<HostAlias> hostAliases = new ArrayList<>();
    private Map<String, String> nodeSelector = new HashMap<>();

    private final Map<String, Quantity> seleniumPodLimits = new HashMap<>();
    private final Map<String, Quantity> seleniumPodRequests = new HashMap<>();

    private final Environment environment;

    private final Function<PodConfiguration, DoneablePod> createDoneablePod;

    public KubernetesContainerClient(Environment environment,
                                     Function<PodConfiguration, DoneablePod> createDoneablePod,
                                     KubernetesClient client) {
        logger.log(Level.INFO, "Initialising Kubernetes support");

        this.environment = environment;
        this.createDoneablePod = createDoneablePod;
        try {
            this.client = client;
            String kubernetesFlavour;
            if (client.isAdaptable(OpenShiftClient.class)) {
                oClient = client.adapt(OpenShiftClient.class);
                kubernetesFlavour = "OpenShift";
            }
            else {
                kubernetesFlavour = "Vanilla Kubernetes";
                oClient = null;
            }

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

            buildResourceMaps();

            logger.log(Level.INFO,
                    "Kubernetes support initialised.\n"
                            + "\tPod name: {0}\n"
                            + "\tapp label: {1}\n"
                            + "\tzalenium service name: {2}\n"
                            + "\tKubernetes flavour: {3}\n"
                            + "\tSelenium Pod Resource Limits: {4}\n"
                            + "\tSelenium Pod Resource Requests: {5}",
                    new Object[] {hostname, appName, zaleniumAppName, kubernetesFlavour,
                            seleniumPodLimits.toString(), seleniumPodRequests.toString() });
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initialising Kubernetes support.", e);
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

        logger.log(Level.INFO, () -> String.format("%s %s", containerId, Arrays.toString(command)));
        ExecWatch exec = client.pods().withName(containerId).writingOutput(baos).writingError(baos).usingListener(new ExecListener() {

            @Override
            public void onOpen(Response response) {
            }

            @Override
            public void onFailure(Throwable t,
                                  Response response) {
                logger.log(Level.SEVERE, t, () -> String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)));
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
                logger.log(Level.SEVERE, e, () -> String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)));
            } finally {
                exec.close();
            }

            logger.log(Level.INFO, () -> String.format("%s %s", containerId, baos.toString()));

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
    public int getRunningContainers(String image) {
        PodList list = client.pods().withLabels(createdByZaleniumMap).list();
        logger.log(Level.INFO, "Pods in the list " + list.getItems().size());
        int count=0;
        for (Pod pod : list.getItems()) {
            String phase = pod.getStatus().getPhase();
            if ("Running".equalsIgnoreCase(phase) || "Pending".equalsIgnoreCase(phase)) {
                count++;
            }
        }

        return count;
    }

    @Override
    public ContainerCreationStatus createContainer(String zaleniumContainerName, String image, Map<String, String> envVars,
                                String nodePort) {
        String containerIdPrefix = String.format("%s-%s-", zaleniumAppName, nodePort);

        // Convert the environment variables into the kubernetes format.
        List<EnvVar> flattenedEnvVars = envVars.entrySet().stream()
                                            .map(e -> new EnvVar(e.getKey(), e.getValue(), null))
                                            .collect(Collectors.toList());

        Map<String, String> podSelector = new HashMap<>();

        PodConfiguration config = new PodConfiguration();
        config.setClient(client);
        config.setContainerIdPrefix(containerIdPrefix);
        config.setImage(image);
        config.setEnvVars(flattenedEnvVars);
        Map<String, String> labels = new HashMap<>();
        labels.putAll(createdByZaleniumMap);
        labels.putAll(appLabelMap);
        labels.putAll(podSelector);
        config.setLabels(labels);
        config.setMountedSharedFoldersMap(mountedSharedFoldersMap);
        config.setHostAliases(hostAliases);
        config.setNodeSelector(nodeSelector);
        config.setPodLimits(seleniumPodLimits);
        config.setPodRequests(seleniumPodRequests);

        DoneablePod doneablePod = createDoneablePod.apply(config);

        // Create the container
        Pod createdPod = doneablePod.done();
        String containerName = createdPod.getMetadata().getName();
        return new ContainerCreationStatus(true, containerName, nodePort);
    }

    @Override
    public void initialiseContainerEnvironment() {
        // Delete any leftover pods from a previous time
        deleteSeleniumPods();

        // Register a shutdown hook to cleanup pods
        Runtime.getRuntime().addShutdownHook(new Thread(this::deleteSeleniumPods));
    }

    @Override
    public String getContainerIp(String containerName) {
        Pod pod = client.pods().withName(containerName).get();
        if (pod != null) {
            String podIP = pod.getStatus().getPodIP();
            logger.log(Level.FINE, String.format("Pod %s, IP -> %s", containerName, podIP));
            return podIP;
        }
        else {
            return null;
        }
    }

    private void deleteSeleniumPods() {
        logger.log(Level.INFO, "About to clean up any left over selenium pods created by Zalenium");
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
            logger.log(Level.WARNING, "{0} Couldn't find NOVNC_PORT, live preview will not work.", containerId);
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
            logger.log(Level.SEVERE,
                       t,
                       () -> String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)));
        }

        @Override
        public void onClose(int code,
                            String reason) {


            // Dirty hack to workaround the fact that ExecWatch doesn't automatically close any resources
            boolean isClosed = closedResource.getAndSet(true);
            boolean hasErrors = stderr.size() > 0;
            if (!isClosed && hasErrors) {
                logger.log(Level.SEVERE,() -> String.format("%s Copy files command failed with:\n\tcommand: %s\n\t stderr:\n%s",
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
                logger.log(Level.SEVERE,
                        e,
                        () -> String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)));
            }
        }
    }

    @SuppressWarnings("unused")
    private static enum Resources {

        CPU_REQUEST(ResourceType.REQUEST, "cpu", "ZALENIUM_KUBERNETES_CPU_REQUEST"),
        CPU_LIMIT(ResourceType.LIMIT, "cpu", "ZALENIUM_KUBERNETES_CPU_LIMIT"),
        MEMORY_REQUEST(ResourceType.REQUEST, "memory", "ZALENIUM_KUBERNETES_MEMORY_REQUEST"),
        MEMORY_LIMIT(ResourceType.LIMIT, "memory", "ZALENIUM_KUBERNETES_MEMORY_LIMIT");

        private ResourceType resourceType;
        private String requestType;
        private String envVar;

        private Resources(ResourceType resourceType, String requestType, String envVar) {
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

    private static enum ResourceType {
        REQUEST, LIMIT;
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
                        .withImagePullPolicy("Always")
                        .addAllToEnv(config.getEnvVars())
                        .addNewVolumeMount()
                            .withName("dshm")
                            .withMountPath("/dev/shm")
                        .endVolumeMount()
                        .withNewResources()
                            .addToLimits(config.getPodLimits())
                            .addToRequests(config.getPodRequests())
                        .endResources()
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

package de.zalando.ep.zalenium.container;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodFluent.SpecNested;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.openshift.client.OpenShiftClient;
import okhttp3.Response;

public class KubernetesContainerClient implements ContainerClient {
    
    private static final String SELENIUM_NODE_NAME = "seleniumNodeName";

    private static KubernetesContainerClient instance;
    
    private static final Logger logger = Logger.getLogger(KubernetesContainerClient.class.getName());
    
    private final KubernetesClient client;
    @SuppressWarnings("unused")
    private final OpenShiftClient oClient;
    
    private String hostname;
    
    private String zaleniumServiceName;
    
    private final Pod zaleniumPod;

    private final Map<String, String> createdByZaleniumMap;
    private final Map<String, String> appLabelMap;
    private final Map<String, String> deploymentConfigLabelMap;

    private Optional<VolumeMount> sharedFolderVolumeMount;

    private Volume sharedFolderVolume;
    
    public static KubernetesContainerClient getInstance() {
        if (instance == null) {
            synchronized (KubernetesContainerClient.class) {
                if(instance == null){
                    instance = new KubernetesContainerClient();
                }
            }
        }
        
        return instance;
    }
    
    private KubernetesContainerClient() {
        logger.info("Initialising Kubernetes support");
        
        
        client = new DefaultKubernetesClient();
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
        hostname = findHostname();
        
        zaleniumPod = client.pods().withName(hostname).get();
        
        String appName = zaleniumPod.getMetadata().getLabels().get("app");
        String deploymentConfig = zaleniumPod.getMetadata().getLabels().get("deploymentconfig");
        
        appLabelMap = new HashMap<>();
        appLabelMap.put("app", appName);
        
        deploymentConfigLabelMap = new HashMap<>();
        deploymentConfigLabelMap.put("deploymentconfig", deploymentConfig);
        
        createdByZaleniumMap = new HashMap<>();
        createdByZaleniumMap.put("createdBy", appName);
        
        discoverSharedFolderMount();
        
        // Lets assume that the zalenium service name is the same as the app name.
        zaleniumServiceName = appName;
        
        logger.log(Level.INFO,
                   "Kubernetes support initialised.\n\tPod name: {0}\n\tapp label: {1}\n\tzalenium service name: {2}\n\tKubernetes flavour: {3}",
                   new Object[] { hostname, appName, zaleniumServiceName, kubernetesFlavour });
        
    }

    private void discoverSharedFolderMount() {
        List<VolumeMount> volumeMounts = zaleniumPod.getSpec().getContainers().get(0).getVolumeMounts();
        
        // Look through the volume mounts to see if the shared folder is mounted
        sharedFolderVolumeMount = volumeMounts.stream().filter(mount -> SHARED_FOLDER_MOUNT_POINT.equals(mount.getMountPath())).findFirst();
        
        if (sharedFolderVolumeMount.isPresent()) {
            List<Volume> volumes = zaleniumPod.getSpec().getVolumes();
            
            // Look for the underlying volume by volume mount name
            sharedFolderVolume = volumes.stream().filter(volume -> sharedFolderVolumeMount.get().getName().equals(volume.getName())).findFirst().get();
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
    public InputStream copyFiles(String containerId,
                                 String folderName) {

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String[] command = new String[] { "tar", "-C", folderName, "-c", "." };
        CopyFilesExecListener listener = new CopyFilesExecListener(stderr, command, containerId);
        ExecWatch exec = client.pods().withName(containerId).redirectingOutput().writingError(stderr).usingListener(listener).exec(command);
        
        // FIXME: This is a bit dodgy, but we need the listener to be able to close the ExecWatch in failure conditions,
        // because it doesn't cleanup properly and deadlocks.
        // Needs bugs fixed inside kubernetes-client.
        listener.setExecWatch(exec);
        
        return exec.getOutput();
    }

    @Override
    public void stopContainer(String containerId) {
        client.pods().withName(containerId).delete();
        client.services().withName(containerId).delete();
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

        
        try {
            latch.await();
        }
        catch (InterruptedException e) {
        }
        finally {
            exec.close();
        }
        
        logger.log(Level.INFO, () -> String.format("%s %s", containerId, baos.toString()));
    }

    @Override
    public String getLatestDownloadedImage(String imageName) {
        // TODO Maybe do something here later, try and get an updated version
        // but lets just pass through at the moment
        return imageName;
    }

    @Override
    public String getLabelValue(String image,
                                String label) {
        /*ImageStreamTag imageStreamTag = oClient.imageStreamTags().withName(image).get();
        imageStreamTag.getImage().getDockerImageConfig()*/
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getRunningContainers(String image) {
        PodList list = client.pods().withLabels(createdByZaleniumMap).list();
        
        return list.getItems().size();
    }

    @Override
    public void createContainer(String zaleniumContainerName, String image, Map<String, String> envVars,
                                String nodePort) {
        String containerId = String.format("%s-%s", zaleniumContainerName, nodePort);
        
        // Convert the environment variables into the kubernetes format.
        List<EnvVar> flattenedEnvVars = envVars.entrySet().stream()
                                            .map(e -> new EnvVar(e.getKey(), e.getValue(), null))
                                            .collect(Collectors.toList());
        
        Map<String, String> podSelector = new HashMap<String, String>();
        // In theory we could use the actual container name, but in the future the name might be generated by kubernetes
        // alternately on registration of the node, the label could be updated to the hostname.  But a bug in Openshift/Kubernetes v1.4.0 seems to prevent this
        podSelector.put(SELENIUM_NODE_NAME, UUID.randomUUID().toString());
        
        DoneablePod doneablePod = client.pods()
            .createNew()
            .withNewMetadata()
                .withName(containerId)
                .addToLabels(createdByZaleniumMap)
                .addToLabels(appLabelMap)
                .addToLabels(podSelector)
            .endMetadata()
            .withNewSpec()
                // Add a memory volume that we can use for /dev/shm
                .addNewVolume()
                    .withName("dshm")
                    .withNewEmptyDir()
                        .withMedium("Memory")
                    .endEmptyDir()
                .endVolume()
                .addNewContainer()
                    .withName("selenium-node")
                    .withImage(image)
                    .withImagePullPolicy("Always")
                    .addAllToEnv(flattenedEnvVars)
                    .addNewVolumeMount()
                        .withName("dshm")
                        .withMountPath("/dev/shm")
                    .endVolumeMount()
                .endContainer()
                .withRestartPolicy("Never")
            .endSpec();
        
        
        // Add the shared folder if it is available
        if (sharedFolderVolumeMount.isPresent()) {
            doneablePod = doneablePod
                .editSpec()
                    .addNewVolumeLike(sharedFolderVolume)
                .and()
                    .editFirstContainer()
                        .addNewVolumeMountLike(sharedFolderVolumeMount.get())
                        .endVolumeMount()
                    .endContainer()
                .endSpec();
        }
        
        // Create the container
        doneablePod.done();
    }

    @Override
    public void initialiseContainerEnvironment() {
        // Delete any leftover pods from a previous time
        deleteSeleniumPods();
        
        // Register a shutdown hook to cleanup pods
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            this.deleteSeleniumPods();
         }));
    }

    private void deleteSeleniumPods() {
        logger.info("About to clean up any left over selenium pods created by zalenium");
        client.pods().withLabels(createdByZaleniumMap).delete();
        client.services().withLabels(createdByZaleniumMap).delete();
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
            
            String seleniumNodeNameValue = currentPod.getMetadata().getLabels().get(SELENIUM_NODE_NAME);
            
            // Create a service so that we locate novnc
            Service service = client.services()
                .createNew()
                .withNewMetadata()
                    .withName(containerId)
                    .withLabels(appLabelMap)
                    .withLabels(createdByZaleniumMap)
                .endMetadata()
                .withNewSpec()
                    .withType("NodePort")
                    .addNewPort()
                        .withName("novnc")
                        .withProtocol("TCP")
                        .withPort(noVncPortInt)
                        .withNewTargetPort(noVncPortInt)
                    .endPort()
                    .addToSelector(SELENIUM_NODE_NAME, seleniumNodeNameValue)
                .endSpec()
                .done();
            
            Integer nodePort = service.getSpec().getPorts().get(0).getNodePort();
            registration.setNoVncPort(nodePort);
        }
        else {
            logger.log(Level.WARNING, "{0} Couldn't find NOVNC_PORT, live preview will not work.", containerId);
        }
        
        registration.setContainerId(containerId);
        
        return registration;
    }
    
    private final class CopyFilesExecListener implements ExecListener {
        private AtomicBoolean closedResource = new AtomicBoolean(false);
        private ExecWatch execWatch;
        private String containerId;
        ByteArrayOutputStream stderr;
        String[] command;
        
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
    }

}

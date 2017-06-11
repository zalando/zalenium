package de.zalando.ep.zalenium.container;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ContainerMount;
import com.spotify.docker.client.messages.ExecCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.Image;
import com.spotify.docker.client.messages.ImageInfo;
import com.spotify.docker.client.messages.PortBinding;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;

@SuppressWarnings("ConstantConditions")
public class DockerContainerClient implements ContainerClient {

    private static final String DEFAULT_DOCKER_NETWORK_NAME = "bridge";
    private static final String DEFAULT_DOCKER_NETWORK_MODE = "default";
    private final Logger logger = Logger.getLogger(DockerContainerClient.class.getName());
    private final GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private DockerClient dockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    private String nodeId;
    private String zaleniumNetwork;
    private ContainerMount mntFolder;
    private boolean mntFolderChecked = false;

    @VisibleForTesting
    public void setContainerClient(final DockerClient client) {
        dockerClient = client;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    private String getContainerId(String containerName) {
        final String containerNameSearch = containerName.contains("/") ?
                containerName : String.format("/%s", containerName);

        List<Container> containerList = null;
        try {
            containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.FINE, nodeId + " Error while getting containerId", e);
            ga.trackException(e);
        }

        return containerList.stream()
                .filter(container -> containerNameSearch.equalsIgnoreCase(container.names().get(0)))
                .findFirst().get().id();
    }

    public InputStream copyFiles(String containerId, String folderName) {
        try {
            return dockerClient.archiveContainer(containerId, folderName);
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Something happened while copying the folder " + folderName + ", " +
                    "most of the time it is an issue while closing the input/output stream, which is usually OK.", e);
        }
        return null;
    }

    public void stopContainer(String containerId) {
        try {
            dockerClient.stopContainer(containerId, 5);
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while stopping the container", e);
            ga.trackException(e);
        }
    }

    public void executeCommand(String containerId, String[] command, boolean waitForExecution) {
        final ExecCreation execCreation;
        try {
            execCreation = dockerClient.execCreate(containerId, command,
                    DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
            final LogStream output = dockerClient.execStart(execCreation.id());
            logger.log(Level.INFO, () -> String.format("%s %s", nodeId, Arrays.toString(command)));
            if (waitForExecution) {
                try {
                    logger.log(Level.INFO, () -> String.format("%s %s", nodeId, output.readFully()));
                } catch (Exception e) {
                    logger.log(Level.FINE, nodeId + " Error while executing the output.readFully()", e);
                    ga.trackException(e);
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.FINE, nodeId + " Error while executing the command", e);
            ga.trackException(e);
        }
    }

    public String getLatestDownloadedImage(String imageName) {
        List<Image> images;
        try {
            images = dockerClient.listImages(DockerClient.ListImagesParam.byName(imageName));
            if (images.isEmpty()) {
                logger.log(Level.SEVERE, nodeId + " A downloaded docker-selenium image was not found!");
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
            logger.log(Level.WARNING, nodeId + " Error while executing the command", e);
            ga.trackException(e);
        }
        return imageName;
    }

    public String getLabelValue(String image, String label) {
        try {
            ImageInfo imageInfo = dockerClient.inspectImage(image);
            return imageInfo.config().labels().get(label);
        } catch (Exception e) {
            logger.log(Level.WARNING, nodeId + " Error while getting label value", e);
            ga.trackException(e);
        }
        return null;
    }

    public int getRunningContainers(String image) {
        try {
            List<Container> containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
            int numberOfDockerSeleniumContainers = 0;
            for (Container container : containerList) {
                if (container.image().contains(image) && !"exited".equalsIgnoreCase(container.state())) {
                    numberOfDockerSeleniumContainers++;
                }
            }
            return numberOfDockerSeleniumContainers;
        } catch (InterruptedException | DockerException e) {
            logger.log(Level.WARNING, nodeId + " Error while getting number of running containers", e);
            ga.trackException(e);
        }
        return 0;
    }

    public void createContainer(String zaleniumContainerName, String image, Map<String, String> envVars,
                                String nodePort) {
        String containerName = generateContainerName(zaleniumContainerName, nodePort);

        List<String> binds = new ArrayList<>();
        binds.add("/dev/shm:/dev/shm");
        loadMountedFolder(zaleniumContainerName);
        if (this.mntFolder != null) {
            String mountedBind = String.format("%s:%s", this.mntFolder.source(), this.mntFolder.destination());
            binds.add(mountedBind);
        }

        String noVncPort = envVars.get("NOVNC_PORT");

        final Map<String, List<PortBinding>> portBindings = new HashMap<>();
        List<PortBinding> portBindingList = new ArrayList<>();
        portBindingList.add(PortBinding.of("", nodePort));
        portBindings.put(nodePort, portBindingList);
        portBindingList = new ArrayList<>();
        portBindingList.add(PortBinding.of("", noVncPort));
        portBindings.put(noVncPort, portBindingList);

        String networkMode = getZaleniumNetwork(zaleniumContainerName);
        HostConfig hostConfig = HostConfig.builder()
                .appendBinds(binds)
                .portBindings(portBindings)
                .networkMode(networkMode)
                .autoRemove(true)
                .privileged(true)
                .build();

        List<String> flattenedEnvVars = envVars.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());


        final String[] exposedPorts = {nodePort, noVncPort};
        final ContainerConfig containerConfig = ContainerConfig.builder()
                .image(image)
                .env(flattenedEnvVars)
                .exposedPorts(exposedPorts)
                .hostConfig(hostConfig)
                .build();

        try {
            final ContainerCreation container = dockerClient.createContainer(containerConfig, containerName);
            dockerClient.startContainer(container.id());
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while starting a new container", e);
            ga.trackException(e);
        }
    }

    private String generateContainerName(String zaleniumContainerName,
                             String nodePort) {
        return String.format("%s_%s", zaleniumContainerName, nodePort);
    }

    private void loadMountedFolder(String zaleniumContainerName) {
        if (this.mntFolder == null && !this.mntFolderChecked) {
            this.mntFolderChecked = true;
            String containerId = getContainerId(zaleniumContainerName);
            if (containerId == null) {
                return;
            }
            ContainerInfo containerInfo = null;
            try {
                containerInfo = dockerClient.inspectContainer(containerId);
            } catch (DockerException | InterruptedException e) {
                logger.log(Level.WARNING, nodeId + " Error while getting mounted folders.", e);
                ga.trackException(e);
            }
            for (ContainerMount containerMount : containerInfo.mounts()) {
                if (SHARED_FOLDER_MOUNT_POINT.equalsIgnoreCase(containerMount.destination())) {
                    this.mntFolder = containerMount;
                }
            }
        }
    }

    @Override
    public void initialiseContainerEnvironment() {
        // TODO: Move cleanup code from bash to here
        
    }

    @Override
    public ContainerClientRegistration registerNode(String zaleniumContainerName, URL remoteHost) {
        ContainerClientRegistration registration = new ContainerClientRegistration();
        
        Integer noVncPort = remoteHost.getPort() + DockerSeleniumStarterRemoteProxy.NO_VNC_PORT_GAP;
        String containerName = generateContainerName(zaleniumContainerName, Integer.toString(remoteHost.getPort()));
        String containerId = this.getContainerId(containerName);
        registration.setNoVncPort(noVncPort);
        registration.setContainerId(containerId);
        return registration;
    }

    private String getZaleniumNetwork(String zaleniumContainerName) {
        if (zaleniumNetwork != null) {
            return zaleniumNetwork;
        }
        String zaleniumContainerId = getContainerId(zaleniumContainerName);
        try {
            ContainerInfo containerInfo = dockerClient.inspectContainer(zaleniumContainerId);
            ImmutableMap<String, AttachedNetwork> networks = containerInfo.networkSettings().networks();
            for (Map.Entry<String, AttachedNetwork> networkEntry : networks.entrySet()) {
                if (!DEFAULT_DOCKER_NETWORK_NAME.equalsIgnoreCase(networkEntry.getKey())) {
                    zaleniumNetwork = networkEntry.getKey();
                    return zaleniumNetwork;
                }
            }
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.FINE, nodeId + " Error while getting Zalenium network.", e);
            ga.trackException(e);
        }
        zaleniumNetwork = DEFAULT_DOCKER_NETWORK_MODE;
        return zaleniumNetwork;
    }
}


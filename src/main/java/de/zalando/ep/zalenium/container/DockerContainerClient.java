package de.zalando.ep.zalenium.container;

import com.google.common.annotations.VisibleForTesting;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("ConstantConditions")
public class DockerContainerClient implements ContainerClient {

    private static final DockerClient defaultDockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    private static final Logger logger = Logger.getLogger(DockerContainerClient.class.getName());
    private static final GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
    private static DockerClient dockerClient = defaultDockerClient;
    private String nodeId;

    @VisibleForTesting
    public static void setContainerClient(final DockerClient client) {
        dockerClient = client;
    }

    @VisibleForTesting
    public static void restoreDockerContainerClient() {
        dockerClient = defaultDockerClient;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getContainerId(String containerName) {
        List<Container> containerList = null;
        try {
            containerList = dockerClient.listContainers(DockerClient.ListContainersParam.allContainers());
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while getting containerId", e);
            ga.trackException(e);
        }
        for (Container container : containerList) {
            if (containerName.equalsIgnoreCase(container.names().get(0))) {
                return container.id();
            }
        }
        return null;
    }

    public TarArchiveInputStream copyFiles(String containerId, String folderName) {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(dockerClient.archiveContainer(containerId,
                folderName))) {
            return tarStream;
        } catch (Exception e) {
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

    public void executeCommand(String containerId, String[] command) {
        final ExecCreation execCreation;
        try {
            execCreation = dockerClient.execCreate(containerId, command,
                    DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
            final LogStream output = dockerClient.execStart(execCreation.id());
            logger.log(Level.INFO, () -> String.format("%s %s", nodeId, Arrays.toString(command)));
            logger.log(Level.INFO, () -> String.format("%s %s", nodeId, output.readFully()));
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while executing the command", e);
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
        } catch (DockerException | InterruptedException e) {
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

    public void createContainer(String zaleniumContainerName, String containerName, String image, List<String> envVars) {
        String networkMode = String.format("container:%s", zaleniumContainerName);
        HostConfig hostConfig = HostConfig.builder()
                .networkMode(networkMode)
                .appendBinds("/dev/shm:/dev/shm")
                .appendBinds("/tmp/mounted:/tmp/mounted")
                .autoRemove(true)
                .build();

        final ContainerConfig containerConfig = ContainerConfig.builder()
                .image(image)
                .env(envVars)
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

}

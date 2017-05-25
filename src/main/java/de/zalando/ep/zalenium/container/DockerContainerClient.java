package de.zalando.ep.zalenium.container;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ExecCreation;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DockerContainerClient implements ContainerClient {

    private static final DockerClient dockerClient = new DefaultDockerClient("unix:///var/run/docker.sock");
    private static final Logger logger = Logger.getLogger(DockerContainerClient.class.getName());
    private static final GoogleAnalyticsApi ga = new GoogleAnalyticsApi();

    @SuppressWarnings("ConstantConditions")
    public String getContainerId(String containerName, String nodeId) {
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

    public TarArchiveInputStream copyFiles(String containerId, String folderName, String nodeId) {
        try (TarArchiveInputStream tarStream = new TarArchiveInputStream(dockerClient.archiveContainer(containerId,
                folderName))) {
            return tarStream;
        } catch (Exception e) {
            logger.log(Level.WARNING, nodeId + " Something happened while copying the folder " + folderName + ", " +
                    "most of the time it is an issue while closing the input/output stream, which is usually OK.", e);
        }
        return null;
    }

    public void stopContainer(String containerId, String nodeId) {
        try {
            dockerClient.stopContainer(containerId, 5);
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, nodeId + " Error while stopping the container", e);
            ga.trackException(e);
        }
    }

    public void executeCommand(String containerId, String[] command, String nodeId) {
        final ExecCreation execCreation;
        try {
            execCreation = dockerClient.execCreate(containerId, command,
                    DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr());
            final LogStream output = dockerClient.execStart(execCreation.id());
            logger.log(Level.INFO, () -> String.format("%s %s", nodeId, Arrays.toString(command)));
            logger.log(Level.INFO, () -> String.format("%s %s", nodeId, output.readFully()));
        } catch (DockerException | InterruptedException e) {
            logger.log(Level.WARNING, "Error while executing the command", e);
            ga.trackException(e);
        }
    }

}

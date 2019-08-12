package de.zalando.ep.zalenium.container.swarm;

import com.google.common.collect.ImmutableMap;

import com.spotify.docker.client.AnsiProgressHandler;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.*;

import com.spotify.docker.client.messages.Network;
import com.spotify.docker.client.messages.swarm.*;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static com.spotify.docker.client.DockerClient.ListContainersParam.withStatusCreated;
import static com.spotify.docker.client.DockerClient.ListContainersParam.withStatusRunning;

public class SwarmUtilities {
    private static final String overlayNetwork = ZaleniumConfiguration.getSwarmOverlayNetwork();
    private static final Logger logger = LoggerFactory.getLogger(SwarmUtilities.class.getName());
    private static final Environment defaultEnvironment = new Environment();
    private static final String dockerHost = defaultEnvironment
            .getStringEnvVariable("DOCKER_HOST", "unix:///var/run/docker.sock")
            // https://github.com/spotify/docker-client/issues/946
            .replace("tcp", "http");
    private static final DockerClient dockerClient = new DefaultDockerClient(dockerHost);

    public static synchronized ContainerInfo getContainerByIp(String ipAddress) {
        try {
            List<Network> networks = dockerClient.listNetworks();
            for (Network network : CollectionUtils.emptyIfNull(networks)) {
                Network networkInfo = dockerClient.inspectNetwork(network.name());
                ImmutableMap<String, Network.Container> containers = networkInfo.containers();

                for (Map.Entry<String, Network.Container> container : MapUtils.emptyIfNull(containers).entrySet()) {
                    if (container.getValue().ipv4Address().startsWith(ipAddress)) {
                        return dockerClient.inspectContainer(container.getKey());
                    }
                }
            }
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }

        logger.warn("Failed to get info of Container by IP Address. {} is not listed in any network", ipAddress);

        return null;
    }

    public static synchronized List<Container> getRunningAndCreatedContainers() throws DockerException, InterruptedException {
        return dockerClient.listContainers(withStatusRunning(), withStatusCreated());
    }

    public static synchronized ContainerStatus getContainerByRemoteUrl(URL remoteUrl) throws DockerException, InterruptedException {
        List<Task> tasks = dockerClient.listTasks();
        for (Task task : tasks) {
            for (NetworkAttachment networkAttachment : CollectionUtils.emptyIfNull(task.networkAttachments())) {
                for (String address : networkAttachment.addresses()) {
                    if (address.split("/")[0].equals(remoteUrl.getHost())) {
                        return task.status().containerStatus();
                    }
                }
            }
        }

        return null;
    }

    public static synchronized void stopServiceByContainerId(String containerId) throws DockerException, InterruptedException {
        List<Task> tasks = dockerClient.listTasks();
        for (Task task : tasks) {
            ContainerStatus containerStatus = task.status().containerStatus();
            if (containerStatus != null && containerId.equals(containerStatus.containerId())) {
                String serviceId = task.serviceId();
                Service.Criteria criteria = Service.Criteria.builder()
                        .serviceId(serviceId)
                        .build();
                List<Service> services = dockerClient.listServices(criteria);
                if (!CollectionUtils.isEmpty(services)) {
                    dockerClient.removeService(serviceId);
                }
            }
        }
    }

    public static synchronized Task getTaskByContainerId(String containerId) throws DockerException, InterruptedException {
        List<Task> tasks = dockerClient.listTasks();

        for (Task task : CollectionUtils.emptyIfNull(tasks)) {
            ContainerStatus containerStatus = task.status().containerStatus();

            if (containerStatus != null && containerId.equals(containerStatus.containerId())) {
                return task;
            }
        }

        return null;
    }

    public static synchronized Task getTaskByServiceId(String serviceId) throws DockerException, InterruptedException {
        String serviceName = dockerClient.inspectService(serviceId).spec().name();
        Task.Criteria criteria = Task.Criteria.builder().serviceName(serviceName).build();
        List<Task> tasks = dockerClient.listTasks(criteria);
        Task task = null;

        if (!CollectionUtils.isEmpty(tasks)) {
            task = tasks.get(0);
        }

        return task;
    }

    public static synchronized void pullImageIfNotPresent(String imageName) throws DockerException, InterruptedException {
        List<Image> images = dockerClient.listImages(DockerClient.ListImagesParam.byName(imageName));
        if (CollectionUtils.isEmpty(images)) {
            dockerClient.pull(imageName, new AnsiProgressHandler());
        }
    }

    public static synchronized void startContainer(ContainerConfig containerConfig) throws DockerException, InterruptedException {
        ContainerCreation containerCreation = dockerClient.createContainer(containerConfig);
        dockerClient.startContainer(containerCreation.id());
    }

    public static synchronized ServiceCreateResponse createService(ServiceSpec serviceSpec) throws DockerException, InterruptedException {
        return dockerClient.createService(serviceSpec);
    }

    public static String getSwarmIp(ContainerInfo containerInfo) {
        AttachedNetwork attachedNetwork = MapUtils.emptyIfNull(containerInfo.networkSettings().networks())
                .get(overlayNetwork);
        String ipAddress = attachedNetwork == null ? "" : attachedNetwork.ipAddress();

        if (ipAddress.isEmpty()) {
            logger.warn("Failed to get the swarm IP Address of container {}", containerInfo.id());
        }

        return ipAddress;
    }

    public static boolean isSwarmActive() {
        return !overlayNetwork.isEmpty();
    }
}

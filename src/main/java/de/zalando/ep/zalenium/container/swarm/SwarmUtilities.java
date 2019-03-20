package de.zalando.ep.zalenium.container.swarm;

import com.google.common.collect.ImmutableMap;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.Network;

import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class SwarmUtilities {
    private static final Environment defaultEnvironment = new Environment();
    private static final String dockerHost = defaultEnvironment.getStringEnvVariable("DOCKER_HOST", "unix:///var/run/docker.sock");
    private static final DockerClient dockerClient = new DefaultDockerClient(dockerHost);
    private static final String overlayNetwork = ZaleniumConfiguration.getSwarmOverlayNetwork();
    private static final Logger logger = LoggerFactory.getLogger(SwarmUtilities.class.getName());

    public static ContainerInfo getContainerByIp(String ipAddress) {
        ContainerInfo containerInfo = null;

        try {
            List<Network> networks = dockerClient.listNetworks();
            for (Network network : CollectionUtils.emptyIfNull(networks)) {
                Network networkInfo = dockerClient.inspectNetwork(network.name());
                ImmutableMap<String, Network.Container> containers = networkInfo.containers();

                for (Map.Entry<String, Network.Container> container : MapUtils.emptyIfNull(containers).entrySet()) {
                    if (container.getValue().ipv4Address().startsWith(ipAddress)) {
                        containerInfo = dockerClient.inspectContainer(container.getKey());
                    }
                }
            }
        } catch (DockerException | InterruptedException e) {
            e.printStackTrace();
        }
        if (containerInfo == null) {
            logger.warn("Failed to get info of Container by IP Address. {} is not listed in any network", ipAddress);
        }

        return containerInfo;
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

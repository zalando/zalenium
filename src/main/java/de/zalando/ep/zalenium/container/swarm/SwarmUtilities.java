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

import java.util.List;
import java.util.Map;

public class SwarmUtilities {
    private static final Environment defaultEnvironment = new Environment();
    private static final String dockerHost = defaultEnvironment.getStringEnvVariable("DOCKER_HOST", "unix:///var/run/docker.sock");
    private static final DockerClient dockerClient = new DefaultDockerClient(dockerHost);
    private static final String overlayNetwork = ZaleniumConfiguration.getSwarmOverlayNetwork();

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

        return containerInfo;
    }

    public static String getSwarmIp(ContainerInfo containerInfo) {
        AttachedNetwork attachedNetwork = null;
        ImmutableMap<String, AttachedNetwork> networks = containerInfo.networkSettings().networks();

        if (networks != null) {
            attachedNetwork = networks.get(overlayNetwork);
        }

        return attachedNetwork == null ? "" : attachedNetwork.ipAddress();
    }

    public static boolean isSwarmActive() {
        return !overlayNetwork.isEmpty();
    }
}

package de.zalando.ep.zalenium.container.swarm;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.UnmodifiableIterator;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.Network;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Iterator;

public class SwarmUtilities {
    private static final Environment defaultEnvironment = new Environment();
    private static final String dockerHost = defaultEnvironment.getStringEnvVariable("DOCKER_HOST", "unix:///var/run/docker.sock");
    private static final DockerClient dockerClient = new DefaultDockerClient(dockerHost);

    private static final String overlayNetwork = ZaleniumConfiguration.getSwarmOverlayNetwork();

    public static ContainerInfo getContainerByIp(String ipAddress) {
        ContainerInfo containerInfo = null;

        try {
            List<Network> networks = dockerClient.listNetworks();
            Iterator<Network> networksIterator = networks.iterator();

            while (networksIterator.hasNext() && containerInfo == null) {
                Network network = dockerClient.inspectNetwork(networksIterator.next().name());
                ImmutableMap<String, Network.Container> containers = network.containers();

                if (containers != null) {
                    UnmodifiableIterator<Map.Entry<String, Network.Container>> containersIterator = containers.entrySet().iterator();

                    while (containersIterator.hasNext() && containerInfo == null) {
                        Map.Entry<String, Network.Container> val = containersIterator.next();
                        if (val.getValue().ipv4Address().startsWith(ipAddress)) {
                            containerInfo = dockerClient.inspectContainer(val.getKey());
                        }
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

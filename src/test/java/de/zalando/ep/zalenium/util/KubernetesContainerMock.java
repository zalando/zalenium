package de.zalando.ep.zalenium.util;

import de.zalando.ep.zalenium.container.kubernetes.KubernetesContainerClient;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HostAlias;
import io.fabric8.kubernetes.api.model.HostAliasBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.kubernetes.client.server.mock.OutputStreamMessage;
import org.apache.commons.lang3.RandomStringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KubernetesContainerMock {
    public static KubernetesContainerClient getMockedKubernetesContainerClient() {
        // Mocking the environment variable to return false for video recording enabled
        Environment environment = mock(Environment.class);
        when(environment.getStringEnvVariable("ZALENIUM_KUBERNETES_CPU_REQUEST", null)).thenReturn("250m");
        when(environment.getStringEnvVariable("ZALENIUM_KUBERNETES_CPU_LIMIT", null)).thenReturn("500m");
        when(environment.getStringEnvVariable("ZALENIUM_KUBERNETES_MEMORY_REQUEST", null)).thenReturn("1Gi");
        when(environment.getStringEnvVariable("ZALENIUM_KUBERNETES_MEMORY_LIMIT", null)).thenReturn("4Gi");

        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = "";
        }
        KubernetesServer server = new KubernetesServer();
        server.before();

        Map<String, String> zaleniumPodLabels = new HashMap<>();
        zaleniumPodLabels.put("app", "zalenium");
        zaleniumPodLabels.put("role", "grid");
        Pod zaleniumPod = new PodBuilder()
                .withNewMetadata()
                .withLabels(zaleniumPodLabels)
                .addToLabels(zaleniumPodLabels)
                .withNamespace("test")
                .withGenerateName(hostName)
                .withName(hostName)
                .and().build();

        String videosVolumeName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
        String generalVolumeName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        VolumeMount volumeMountVideos = new VolumeMountBuilder()
                .withName(videosVolumeName)
                .withMountPath("/tmp/videos")
                .build();
        VolumeMount volumeMountGeneral = new VolumeMountBuilder()
                .withName(generalVolumeName)
                .withMountPath("/tmp/mounted")
                .build();

        Container zaleniumContainer = new ContainerBuilder()
                .withVolumeMounts(volumeMountVideos, volumeMountGeneral)
                .build();

        Volume videosVolume = new VolumeBuilder()
                .withName(videosVolumeName)
                .build();
        Volume generalVolume = new VolumeBuilder()
                .withName(generalVolumeName)
                .build();

        HostAlias hostAlias = new HostAliasBuilder()
                .withHostnames("foo.local")
                .withIp("127.0.0.1")
                .build();

        Map<String, String> nodeSelector = new HashMap<>();
        nodeSelector.put("nodeLabelKey", "nodeLabelValue");

        PodSpec zaleniumPodSpec = new PodSpecBuilder()
                .withContainers(zaleniumContainer)
                .withVolumes(videosVolume, generalVolume)
                .withHostAliases(hostAlias)
                .withNodeSelector(nodeSelector)
                .build();
        zaleniumPod.setSpec(zaleniumPodSpec);

        String podsPath = String.format("/api/v1/namespaces/test/pods/%s", hostName);
        server.expect()
                .withPath(podsPath).andReturn(200, zaleniumPod).once();

        Map<String, String> dockerSeleniumPodLabels = new HashMap<>();
        dockerSeleniumPodLabels.put("createdBy", "zalenium");
        Pod dockerSeleniumPod = new PodBuilder()
                .withNewMetadata()
                .withLabels(dockerSeleniumPodLabels)
                .withNamespace("test")
                .withName(hostName)
                .and().build();

        Container dockerSeleniumContainer = new ContainerBuilder()
                .withEnv(new EnvVarBuilder().withName("NOVNC_PORT").withValue("40000").build())
                .build();

        PodSpec dockerSeleniumPodSpec = new PodSpecBuilder()
                .withContainers(dockerSeleniumContainer)
                .build();
        dockerSeleniumPod.setSpec(dockerSeleniumPodSpec);

        PodStatus dockerSeleniumPodStatus = new PodStatusBuilder().withPodIP("localhost").build();
        dockerSeleniumPod.setStatus(dockerSeleniumPodStatus);

        server.expect()
                .withPath("/api/v1/namespaces/test/pods?labelSelector=createdBy%3Dzalenium")
                .andReturn(200, new PodListBuilder().withItems(dockerSeleniumPod).build()).always();

        ServiceSpec serviceSpec = new ServiceSpecBuilder()
                .withPorts(new ServicePortBuilder().withNodePort(40000).build())
                .build();
        Service service = new ServiceBuilder()
                .withSpec(serviceSpec)
                .build();

        server.expect()
                .withPath("/api/v1/namespaces/test/services")
                .andReturn(201, service).always();

        String bashCommand = String.format("/api/v1/namespaces/test/pods/%s/exec?command=bash&command=-c&command=", hostName);
        String tarCommand = String.format("/api/v1/namespaces/test/pods/%s/exec?command=tar&command=-C&command=", hostName);
        String commandSuffix = "&stdout=true&stderr=true";
        String expectedOutput = "test";
        List<String> execPaths = new ArrayList<>();
        execPaths.add(String.format("%stransfer-logs.sh%s", bashCommand, commandSuffix));
        execPaths.add(String.format("%s/var/log/cont/&command=-c&command=.%s", tarCommand, commandSuffix));
        execPaths.add(String.format("%s/videos/&command=-c&command=.%s", tarCommand, commandSuffix));
        execPaths.add(String.format("%s/videos/&command=-C&command=.%s", tarCommand, commandSuffix));
        execPaths.add(String.format("%sstop-video%s", bashCommand, commandSuffix));
        execPaths.add(String.format("%sstart-video%s", bashCommand, commandSuffix));
        execPaths.add(String.format("%stransfer-logs.sh%s", bashCommand, commandSuffix));
        execPaths.add(String.format("%scleanup-container.sh%s", bashCommand, commandSuffix));
        String notifyComplete = bashCommand
                .concat("notify%20%27Zalenium%27,%20%27TEST%20COMPLETED%27,%20--icon=/home/seluser/images/completed.png")
                .concat(commandSuffix);
        String notifyTimeout = bashCommand
                .concat("notify%20%27Zalenium%27,%20%27TEST%20TIMED%20OUT%27,%20--icon=/home/seluser/images/timeout.png")
                .concat(commandSuffix);
        execPaths.add(notifyComplete);
        execPaths.add(notifyTimeout);

        for (String execPath : execPaths) {
            server.expect()
                    .withPath(execPath)
                    .andUpgradeToWebSocket()
                    .open(new OutputStreamMessage(expectedOutput))
                    .done()
                    .once();
        }

        server.expect()
                .withPath("/api/v1/namespaces/test/pods")
                .andReturn(201, new PodBuilder().build())
                .always();

        KubernetesClient client = server.getClient();

        return new KubernetesContainerClient(environment,
                KubernetesContainerClient::createDoneablePodDefaultImpl,
                client);
    }
}

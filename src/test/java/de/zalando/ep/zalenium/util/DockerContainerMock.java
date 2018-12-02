package de.zalando.ep.zalenium.util;

import static de.zalando.ep.zalenium.dashboard.TestInformation.TestStatus.COMPLETED;
import static de.zalando.ep.zalenium.dashboard.TestInformation.TestStatus.TIMEOUT;
import static de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.CLEANUP_CONTAINER;
import static de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.SEND_NOTIFICATION;
import static de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING;
import static de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING;
import static de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.TRANSFER_LOGS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.lang3.RandomStringUtils;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import com.spotify.docker.client.messages.ImageInfo;
import com.spotify.docker.client.messages.Info;
import com.spotify.docker.client.messages.NetworkSettings;

import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.DockerContainerClient;

public class DockerContainerMock {

    public static DockerContainerClient getRegisterOnlyDockerContainerClient() {
        DockerContainerClient mock = mock(DockerContainerClient.class);
        
        when(mock.registerNode(anyString(), any(URL.class))).thenAnswer((Answer<ContainerClientRegistration>) invocation -> {
            String containerName = invocation.getArgument(0);
            URL remoteUrl = invocation.getArgument(1);

            ContainerClientRegistration registration = new ContainerClientRegistration();
            registration.setContainerId(containerName);
            registration.setIpAddress(remoteUrl.getHost());
            registration.setNoVncPort(40000);

            return registration;
        });
        
        return mock;
    }

    
    public static DockerContainerClient getMockedDockerContainerClient() {
        return getMockedDockerContainerClient("default");
    }

    @SuppressWarnings("ConstantConditions")
    public static DockerContainerClient getMockedDockerContainerClient(String networkName) {
        DockerClient dockerClient = mock(DockerClient.class);
        ExecCreation execCreation = mock(ExecCreation.class);
        LogStream logStream = mock(LogStream.class);
        when(logStream.readFully()).thenReturn("ANY_STRING");
        when(execCreation.id()).thenReturn("ANY_ID");

        ContainerCreation containerCreation = mock(ContainerCreation.class);
        when(containerCreation.id()).thenReturn("ANY_CONTAINER_ID");

        AttachedNetwork attachedNetwork = mock(AttachedNetwork.class);
        NetworkSettings networkSettings = mock(NetworkSettings.class);
        HostConfig hostConfig = mock(HostConfig.class);

        ImageInfo imageInfo = mock(ImageInfo.class);
        ContainerConfig containerConfig = mock(ContainerConfig.class);
        ContainerInfo containerInfo = mock(ContainerInfo.class);
        ContainerMount tmpMountedMount = mock(ContainerMount.class);
        when(tmpMountedMount.destination()).thenReturn("/tmp/node/tmp/mounted");
        when(tmpMountedMount.source()).thenReturn("/tmp/mounted");
        ContainerMount homeFolderMount = mock(ContainerMount.class);
        when(homeFolderMount.destination()).thenReturn("/tmp/node/home/seluser/folder");
        when(homeFolderMount.source()).thenReturn("/tmp/folder");
        when(containerInfo.mounts()).thenReturn(ImmutableList.of(tmpMountedMount, homeFolderMount));
        when(attachedNetwork.ipAddress()).thenReturn("127.0.0.1");
        when(networkSettings.networks()).thenReturn(ImmutableMap.of(networkName, attachedNetwork));
        when(networkSettings.ipAddress()).thenReturn("");
        when(containerInfo.networkSettings()).thenReturn(networkSettings);
        when(hostConfig.extraHosts()).thenReturn(null);
        when(containerInfo.hostConfig()).thenReturn(hostConfig);
        String[] httpEnvVars = {"zalenium_http_proxy=http://34.211.100.239:8080",
                "zalenium_https_proxy=http://34.211.100.239:8080"};
        when(containerConfig.env()).thenReturn(ImmutableList.copyOf(Arrays.asList(httpEnvVars)));
        when(containerInfo.config()).thenReturn(containerConfig);

        String containerId = RandomStringUtils.randomAlphabetic(30).toLowerCase();
        Container container_40000 = mock(Container.class);
        when(container_40000.names()).thenReturn(ImmutableList.copyOf(Collections.singletonList("/zalenium_40000")));
        when(container_40000.id()).thenReturn(containerId);
        when(container_40000.status()).thenReturn("running");
        when(container_40000.image()).thenReturn("elgalu/selenium");
        NetworkSettings container_40000NetworkSettings = mock(NetworkSettings.class);
        when(container_40000NetworkSettings.ipAddress()).thenReturn("localhost");
        AttachedNetwork container_40000Attached = mock(AttachedNetwork.class);
        when(container_40000Attached.ipAddress()).thenReturn("localhost");
        when(container_40000NetworkSettings.networks()).thenReturn(ImmutableMap.of("network", container_40000Attached));
        when(container_40000.networkSettings()).thenReturn(container_40000NetworkSettings);
        
        Container container_40001 = mock(Container.class);
        when(container_40001.names()).thenReturn(ImmutableList.copyOf(Collections.singletonList("/zalenium_40001")));
        when(container_40001.id()).thenReturn(containerId);
        when(container_40001.status()).thenReturn("running");
        when(container_40001.image()).thenReturn("elgalu/selenium");
        NetworkSettings container_40001NetworkSettings = mock(NetworkSettings.class);
        when(container_40001NetworkSettings.ipAddress()).thenReturn("localhost");
        AttachedNetwork container_40001Attached = mock(AttachedNetwork.class);
        when(container_40001Attached.ipAddress()).thenReturn("localhost");
        when(container_40001NetworkSettings.networks()).thenReturn(ImmutableMap.of("network", container_40001Attached));
        when(container_40001.networkSettings()).thenReturn(container_40001NetworkSettings);
        
        String zaleniumContainerId = RandomStringUtils.randomAlphabetic(30).toLowerCase();
        Container zalenium = mock(Container.class);
        when(zalenium.names()).thenReturn(ImmutableList.copyOf(Collections.singletonList("/zalenium")));
        when(zalenium.id()).thenReturn(zaleniumContainerId);
        when(zalenium.status()).thenReturn("running");
        when(zalenium.image()).thenReturn("dosel/zalenium");

        Info dockerInfo = mock(Info.class);
        when(dockerInfo.name()).thenReturn("ubuntu_vm");
        
        try {
            URL logsLocation = TestUtils.class.getClassLoader().getResource("logs.tar");
            URL videosLocation = TestUtils.class.getClassLoader().getResource("videos.tar");
            File logsFile = new File(logsLocation.getPath());
            File videosFile = new File(videosLocation.getPath());
            when(dockerClient.archiveContainer(containerId, "/var/log/cont/")).thenReturn(new FileInputStream(logsFile));
            when(dockerClient.archiveContainer(containerId, "/videos/")).thenReturn(new FileInputStream(videosFile));

            String[] startVideo = {"bash", "-c", START_RECORDING.getContainerAction()};
            String[] stopVideo = {"bash", "-c", STOP_RECORDING.getContainerAction()};
            String[] transferLogs = {"bash", "-c", TRANSFER_LOGS.getContainerAction()};
            String[] cleanupContainer = {"bash", "-c", CLEANUP_CONTAINER.getContainerAction()};
            String[] sendNotificationCompleted = {"bash", "-c",
                    SEND_NOTIFICATION.getContainerAction().concat(COMPLETED.getTestNotificationMessage())};
            String[] sendNotificationTimeout = {"bash", "-c",
                    SEND_NOTIFICATION.getContainerAction().concat(TIMEOUT.getTestNotificationMessage())};
            when(dockerClient.execCreate(containerId, startVideo, DockerClient.ExecCreateParam.attachStdout(),
                    DockerClient.ExecCreateParam.attachStderr(), DockerClient.ExecCreateParam.attachStdin()))
                    .thenReturn(execCreation);
            when(dockerClient.execCreate(containerId, stopVideo, DockerClient.ExecCreateParam.attachStdout(),
                    DockerClient.ExecCreateParam.attachStderr(), DockerClient.ExecCreateParam.attachStdin()))
                    .thenReturn(execCreation);
            when(dockerClient.execCreate(containerId, transferLogs, DockerClient.ExecCreateParam.attachStdout(),
                    DockerClient.ExecCreateParam.attachStderr(), DockerClient.ExecCreateParam.attachStdin()))
                    .thenReturn(execCreation);
            when(dockerClient.execCreate(containerId, cleanupContainer, DockerClient.ExecCreateParam.attachStdout(),
                    DockerClient.ExecCreateParam.attachStderr(), DockerClient.ExecCreateParam.attachStdin()))
                    .thenReturn(execCreation);
            when(dockerClient.execCreate(containerId, sendNotificationCompleted, DockerClient.ExecCreateParam.attachStdout(),
                    DockerClient.ExecCreateParam.attachStderr(), DockerClient.ExecCreateParam.attachStdin()))
                    .thenReturn(execCreation);
            when(dockerClient.execCreate(containerId, sendNotificationTimeout, DockerClient.ExecCreateParam.attachStdout(),
                    DockerClient.ExecCreateParam.attachStderr(), DockerClient.ExecCreateParam.attachStdin()))
                    .thenReturn(execCreation);

            when(dockerClient.execStart(anyString())).thenReturn(logStream);
            doNothing().when(dockerClient).stopContainer(anyString(), anyInt());

            when(dockerClient.info()).thenReturn(dockerInfo);

            when(dockerClient.createContainer(any(ContainerConfig.class), anyString())).thenReturn(containerCreation);

            when(dockerClient.listContainers(DockerClient.ListContainersParam.allContainers()))
                    .thenReturn(Arrays.asList(container_40000, container_40001, zalenium));

            when(containerConfig.labels()).thenReturn(ImmutableMap.of("selenium_firefox_version", "52",
                    "selenium_chrome_version", "58"));
            when(imageInfo.config()).thenReturn(containerConfig);
            when(dockerClient.inspectContainer(null)).thenReturn(containerInfo);
            when(dockerClient.inspectContainer(zaleniumContainerId)).thenReturn(containerInfo);
            when(dockerClient.inspectContainer(containerId)).thenReturn(containerInfo);

            when(dockerClient.inspectImage(anyString())).thenReturn(imageInfo);

            when(dockerClient.listImages(DockerClient.ListImagesParam.byName("elgalu/selenium")))
                    .thenReturn(Collections.emptyList());

        } catch (DockerException | InterruptedException | IOException e) {
            e.printStackTrace();
        }

        DockerContainerClient dockerContainerClient = new DockerContainerClient();
        dockerContainerClient.setContainerClient(dockerClient);
        return dockerContainerClient;
    }
}

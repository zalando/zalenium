package de.zalando.ep.zalenium.container;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.util.List;

public interface ContainerClient {

    String getContainerId(String containerName, String nodeId);

    TarArchiveInputStream copyFiles(String containerId, String folderName, String nodeId);

    void stopContainer(String containerId, String nodeId);

    void executeCommand(String containerId, String[] command, String nodeId);

    String getLatestDownloadedImage(String imageName, String nodeId);

    String getLabelValue(String image, String label, String nodeId);

    int getRunningContainers(String image, String nodeId);

    void createContainer(String zaleniumContainerName, String containerName, String image, List<String> envVars);
}

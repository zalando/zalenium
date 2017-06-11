package de.zalando.ep.zalenium.container;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;

public interface ContainerClient {
    
    public static final String SHARED_FOLDER_MOUNT_POINT = "/tmp/mounted";

    void setNodeId(String nodeId);

    ContainerClientRegistration registerNode(String zaleniumContainerName, URL remoteHost);

    InputStream copyFiles(String containerId, String folderName);

    void stopContainer(String containerId);

    void executeCommand(String containerId, String[] command, boolean waitForExecution);

    String getLatestDownloadedImage(String imageName);

    String getLabelValue(String image, String label);

    int getRunningContainers(String image);

    void createContainer(String zaleniumContainerName, String image, Map<String, String> envVars, String nodePort);

    void initialiseContainerEnvironment();
}

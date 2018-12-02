package de.zalando.ep.zalenium.container;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;

public interface ContainerClient {
    
    void setNodeId(String nodeId);

    ContainerClientRegistration registerNode(String zaleniumContainerName, URL remoteHost);

    InputStream copyFiles(String containerId, String folderName);

    void stopContainer(String containerId);

    void executeCommand(String containerId, String[] command, boolean waitForExecution);

    String getLatestDownloadedImage(String imageName);

    ContainerCreationStatus createContainer(String zaleniumContainerName, String image, Map<String, String> envVars, String nodePort);

    void initialiseContainerEnvironment();

    String getContainerIp(String containerName);
    
    boolean isReady(ContainerCreationStatus container);
    
    boolean isTerminated(ContainerCreationStatus container);
}

package de.zalando.ep.zalenium.container;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

// This interface simply defines the methods that must be implemented in any class that wants to create the
// containers used by Zalenium
public interface ContainerClient {

    String getContainerId(String containerName, String nodeId);

    TarArchiveInputStream copyFiles(String containerId, String folderName, String nodeId);

    void stopContainer(String containerId, String nodeId);

    void executeCommand(String containerId, String[] command, String nodeId);

}

package de.zalando.ep.zalenium.container;

public class ContainerCreationStatus {

    private boolean isCreated;
    
    private String containerName;
    
    private String nodePort;

    public ContainerCreationStatus(boolean isCreated) {
        super();
        this.isCreated = isCreated;
    }

    public ContainerCreationStatus(boolean isCreated, String containerName, String nodePort) {
        super();
        this.isCreated = isCreated;
        this.containerName = containerName;
        this.nodePort = nodePort;
    }

    public boolean isCreated() {
        return isCreated;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getNodePort() {
        return nodePort;
    }

    @Override
    public String toString() {
        return "ContainerCreationStatus [isCreated=" + isCreated + ", containerName=" + containerName + ", nodePort="
                + nodePort + "]";
    }
}

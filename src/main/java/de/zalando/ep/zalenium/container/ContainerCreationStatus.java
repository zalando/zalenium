package de.zalando.ep.zalenium.container;

public class ContainerCreationStatus {

    private boolean isCreated;
    
    private String containerName;
    
    private String containerId;
    
    private String nodePort;

    public ContainerCreationStatus(boolean isCreated) {
        super();
        this.isCreated = isCreated;
    }

    public ContainerCreationStatus(boolean isCreated, String containerName, String containerId, String nodePort) {
        super();
        this.isCreated = isCreated;
        this.containerName = containerName;
		    this.containerId = containerId;
        this.nodePort = nodePort;
    }

    public boolean isCreated() {
        return isCreated;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getContainerId() {
		return containerId;
	}
    
    public String getNodePort() {
        return nodePort;
    }

	@Override
	public String toString() {
		return "ContainerCreationStatus [isCreated=" + isCreated + ", containerName=" + containerName + ", containerId="
				+ containerId + ", nodePort=" + nodePort + "]";
	}
    
}

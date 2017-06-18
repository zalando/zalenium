package de.zalando.ep.zalenium.container;

public class ContainerClientRegistration {

    private String containerId;
    
    private Integer noVncUrlPort;

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public Integer getNoVncPort() {
        return noVncUrlPort;
    }

    public void setNoVncPort(Integer noVncUrlPort) {
        this.noVncUrlPort = noVncUrlPort;
    }
    
}

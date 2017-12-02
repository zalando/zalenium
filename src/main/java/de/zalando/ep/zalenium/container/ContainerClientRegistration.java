package de.zalando.ep.zalenium.container;

public class ContainerClientRegistration {

    private String containerId;
    
    private Integer noVncUrlPort;
    
    private String ipAddress;

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
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getIpAddress() {
        return this.ipAddress;
    }
}

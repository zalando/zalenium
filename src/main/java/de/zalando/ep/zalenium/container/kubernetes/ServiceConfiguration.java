package de.zalando.ep.zalenium.container.kubernetes;

import java.util.Map;

import io.fabric8.kubernetes.client.KubernetesClient;

public class ServiceConfiguration {

    private KubernetesClient client;
    private Map<String, String> labels;
    private String name;
    private Integer noVncPort;
    private Map<String, String> selectors;
    
    public KubernetesClient getClient() {
        return client;
    }
    public void setClient(KubernetesClient client) {
        this.client = client;
    }
    public Map<String, String> getLabels() {
        return labels;
    }
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Integer getNoVncPort() {
        return noVncPort;
    }
    public void setNoVncPort(Integer noVncPort) {
        this.noVncPort = noVncPort;
    }
    public Map<String, String> getSelectors() {
        return selectors;
    }
    public void setSelectors(Map<String, String> selectors) {
        this.selectors = selectors;
    }
    
}

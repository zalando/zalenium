package de.zalando.ep.zalenium.util;

import java.util.Map;

public class ProcessedCapabilities {
    private Map<String, Object> requestedCapability;
    private int identityHashCode;
    private long lastProcessedTime;
    private int processedTimes;
    private final long firstProcessedTime;

    public ProcessedCapabilities(Map<String, Object> requestedCapability, int identityHashCode) {
        this.requestedCapability = requestedCapability;
        this.identityHashCode = identityHashCode;
        this.lastProcessedTime = System.currentTimeMillis();
        this.firstProcessedTime = this.lastProcessedTime;
        this.processedTimes = 1;
    }

    public Map<String, Object> getRequestedCapability() {
        return requestedCapability;
    }

    public void setRequestedCapability(Map<String, Object> requestedCapability) {
        this.requestedCapability = requestedCapability;
    }

    public int getIdentityHashCode() {
        return identityHashCode;
    }

    public long getLastProcessedTime() {
        return lastProcessedTime;
    }

    public void setLastProcessedTime(long lastProcessedTime) {
        this.lastProcessedTime = lastProcessedTime;
    }

    public int getProcessedTimes() {
        return processedTimes;
    }

    public void setProcessedTimes(int processedTimes) {
        this.processedTimes = processedTimes;
    }

    public long getFirstProcessedTime() {
        return firstProcessedTime;
    }
}

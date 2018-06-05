package de.zalando.ep.zalenium.proxy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.zalando.ep.zalenium.util.ProcessedCapabilities;

public class SessionRequestFilter {

	private static final Logger log = Logger.getLogger(SessionRequestFilter.class.getName());
	
	private final Map<Integer, ProcessedCapabilities> processedCapabilitiesMap = new ConcurrentHashMap<>();

    public boolean hasRequestBeenProcessed(Map<String, Object> requestedCapability) {
        int requestedCapabilityHashCode = System.identityHashCode(requestedCapability);
        ProcessedCapabilities processedCapability = processedCapabilitiesMap.get(requestedCapabilityHashCode);
        
        if (processedCapability != null) {
            processedCapability.setLastProcessedTime(System.currentTimeMillis());
            int processedTimes = processedCapability.getProcessedTimes() + 1;
            processedCapability.setProcessedTimes(processedTimes);

            if (processedTimes >= 30) {
                processedCapability.setProcessedTimes(1);
                log.log(Level.INFO, "Request has waited 30 attempts for a node, something " +
                        "went wrong with the previous attempts, creating a new node for {0}.", requestedCapability);
                return false;
            }

            return true;
        }

        return false;
    }
    
    public void requestHasBeenProcesssed(Map<String, Object> desiredCapabilities) {
        ProcessedCapabilities processedCapabilities = new ProcessedCapabilities(desiredCapabilities,
                System.identityHashCode(desiredCapabilities));
        processedCapabilitiesMap.put(processedCapabilities.getIdentityHashCode(), processedCapabilities);
    }
    
    /**
     * Notify the Session Request Filter that a Test Session has started for the given desiredCapabilities.
     * 
     * @param desiredCapabilities The desiredCapabilities to check
     * @return true if a desiredCapability had been processed before, false if it was never seen.
     */
    public boolean testSessionHasStarted(Map<String, Object> desiredCapabilities) {
        int desiredCapabilityHashCode = System.identityHashCode(desiredCapabilities);
        ProcessedCapabilities processedCapability = processedCapabilitiesMap.remove(desiredCapabilityHashCode);
        boolean isSeenBefore = processedCapability != null;

        return isSeenBefore;
    }
    
    public void cleanProcessedCapabilities() {
        /*
            Cleaning processed capabilities to reduce the risk of having two objects with the same
            identityHashCode after the garbage collector did its job.
            Not a silver bullet solution, but should be good enough.
         */
        processedCapabilitiesMap.entrySet().stream().filter( cap -> {
            long timeSinceLastProcess = System.currentTimeMillis() - cap.getValue().getLastProcessedTime();
            long maximumLastProcessedTime = 1000 * 60;
            return timeSinceLastProcess >= maximumLastProcessedTime;
        })
        // When you return null on a compute, it deletes the entry from the map
        .forEach(cap -> processedCapabilitiesMap.compute(cap.getKey(), (hash, capability) -> null));
    }
}

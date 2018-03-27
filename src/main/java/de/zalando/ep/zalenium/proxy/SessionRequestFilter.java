package de.zalando.ep.zalenium.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.zalando.ep.zalenium.util.ProcessedCapabilities;

public class SessionRequestFilter {

	private static final Logger log = Logger.getLogger(SessionRequestFilter.class.getName());
	
	private final List<ProcessedCapabilities> processedCapabilitiesList = new ArrayList<>();

    public boolean hasRequestBeenProcessed(Map<String, Object> requestedCapability) {
        int requestedCapabilityHashCode = System.identityHashCode(requestedCapability);
        for (ProcessedCapabilities processedCapability : processedCapabilitiesList) {

        	log.log(Level.FINE, "System.identityHashCode(requestedCapability) -> "
                    + System.identityHashCode(requestedCapability) + ", " + requestedCapability);
        	log.log(Level.FINE, "processedCapability.getIdentityHashCode() -> "
                    + processedCapability.getIdentityHashCode() + ", " + processedCapability.getRequestedCapability());

            if (processedCapability.getIdentityHashCode() == requestedCapabilityHashCode) {

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

        }
        return false;
    }
    
    public void requestHasBeenProcesssed(Map<String, Object> desiredCapabilities) {
        ProcessedCapabilities processedCapabilities = new ProcessedCapabilities(desiredCapabilities,
                System.identityHashCode(desiredCapabilities));
        processedCapabilitiesList.add(processedCapabilities);
    }
    
    public void cleanProcessedCapabilities() {
        /*
            Cleaning processed capabilities to reduce the risk of having two objects with the same
            identityHashCode after the garbage collector did its job.
            Not a silver bullet solution, but should be good enough.
         */
        List<ProcessedCapabilities> processedCapabilitiesToRemove = new ArrayList<>();
        for (ProcessedCapabilities processedCapability : processedCapabilitiesList) {
            long timeSinceLastProcess = System.currentTimeMillis() - processedCapability.getLastProcessedTime();
            long maximumLastProcessedTime = 1000 * 60;
            if (timeSinceLastProcess >= maximumLastProcessedTime) {
                processedCapabilitiesToRemove.add(processedCapability);
            }
        }
        processedCapabilitiesList.removeAll(processedCapabilitiesToRemove);
    }
	
}

package de.zalando.ep.zalenium.proxy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.zalando.ep.zalenium.util.ProcessedCapabilities;
import io.prometheus.client.Collector;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public class SessionRequestFilter {

	private static final Logger log = Logger.getLogger(SessionRequestFilter.class.getName());
	
	private final Map<Integer, ProcessedCapabilities> processedCapabilitiesMap = new ConcurrentHashMap<>();

	static final Gauge seleniumTestSessionsWaiting = Gauge.build()
            .name("selenium_test_sessions_waiting").help("The number of Selenium test sessions that are waiting for a container").register();
	
	static final Histogram seleniumTestSessionStartLatency = Histogram.build()
	        .name("selenium_test_session_start_latency_seconds")
	        .help("The Selenium test session start time latency in seconds.")
	        .buckets(0.5,2.5,5,10,15,20,25,30,35,40,50,60)
	        .register();
	
    public boolean hasRequestBeenProcessed(Map<String, Object> requestedCapability) {
        int requestedCapabilityHashCode = System.identityHashCode(requestedCapability);
        for (ProcessedCapabilities processedCapability : processedCapabilitiesMap.values()) {

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
        processedCapabilitiesMap.put(processedCapabilities.getIdentityHashCode(), processedCapabilities);
        seleniumTestSessionsWaiting.inc();
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
        long elapsedTime;
        
        if (isSeenBefore) {
            // Since we've seen this test session before, then we can say it's not waiting for a container anymore.
            seleniumTestSessionsWaiting.dec();

            // Calculate how long the test session request was waiting.
            elapsedTime = System.currentTimeMillis() - processedCapability.getFirstProcessedTime();
        }
        else {
            // Since we don't actually know how long the request has been around, lets just say it's 100
            elapsedTime = 100;
        }
        
        seleniumTestSessionStartLatency.observe(elapsedTime / Collector.MILLISECONDS_PER_SECOND);
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

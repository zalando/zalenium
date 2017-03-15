package de.zalando.tip.zalenium.util;

import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The purpose of this class is to let docker-selenium process requests where a capability "version=latest" is present
 */
public class DockerSeleniumCapabilityMatcher extends DefaultCapabilityMatcher {
    private static final Logger logger = Logger.getLogger(DockerSeleniumCapabilityMatcher.class.getName());

    @Override
    public boolean matches(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        logger.log(Level.FINE, ()-> String.format("Validating %s in node with capabilities %s", requestedCapability,
                nodeCapability));

        /*
            If after removing 'latest', the capabilities match docker-selenium, we leave the requestedCapabilities
            without the version. If not, we put the requested capability back in the requestedCapability object, so it
            can be matched by any of the Cloud Testing Providers.
         */
        if (requestedCapability.containsKey(CapabilityType.VERSION)) {
            String requestedVersion = requestedCapability.get(CapabilityType.VERSION).toString();
            if ("latest".equalsIgnoreCase(requestedVersion)) {
                requestedCapability.remove(CapabilityType.VERSION);
                if (super.matches(nodeCapability, requestedCapability)) {
                    return true;
                } else {
                    requestedCapability.put(CapabilityType.VERSION, requestedVersion);
                    return false;
                }
            }
        }
        return super.matches(nodeCapability, requestedCapability);
    }
}

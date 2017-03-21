package de.zalando.tip.zalenium.util;

import de.zalando.tip.zalenium.proxy.DockerSeleniumRemoteProxy;
import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The purpose of this class is to let docker-selenium process requests where a capability "version=latest" is present
 */
public class DockerSeleniumCapabilityMatcher extends DefaultCapabilityMatcher {
    private static final Logger logger = Logger.getLogger(DockerSeleniumCapabilityMatcher.class.getName());

    private DefaultRemoteProxy proxy;

    public DockerSeleniumCapabilityMatcher(DefaultRemoteProxy defaultRemoteProxy) {
        super();
        proxy = defaultRemoteProxy;
    }

    @Override
    public boolean matches(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        logger.log(Level.FINE, ()-> String.format("Validating %s in node with capabilities %s", requestedCapability,
                nodeCapability));

        /*
            If after removing 'latest', the capabilities match docker-selenium, we leave the requestedCapabilities
            without the version. If not, we put the requested capability back in the requestedCapability object, so it
            can be matched by any of the Cloud Testing Providers.
         */
        boolean browserVersionCapabilityMatches = false;
        if (requestedCapability.containsKey(CapabilityType.VERSION)) {
            String requestedVersion = requestedCapability.get(CapabilityType.VERSION).toString();
            if ("latest".equalsIgnoreCase(requestedVersion)) {
                requestedCapability.remove(CapabilityType.VERSION);
                if (super.matches(nodeCapability, requestedCapability)) {
                    browserVersionCapabilityMatches = true;
                } else {
                    requestedCapability.put(CapabilityType.VERSION, requestedVersion);
                }
            }
        }

        boolean screenResolutionCapabilityMatches = true;
        // This validation is only done for docker-selenium nodes
        if (proxy instanceof DockerSeleniumRemoteProxy) {
            String[] screenResolutionNames = {"screenResolution", "resolution", "screen-resolution"};
            for (String screenResolutionName : screenResolutionNames) {
                if (requestedCapability.containsKey(screenResolutionName)) {
                    screenResolutionCapabilityMatches = nodeCapability.containsKey(screenResolutionName) &&
                            requestedCapability.get(screenResolutionName).equals(nodeCapability.get(screenResolutionName));
                }
            }
        }

        // If the browser version has been matched, then implicitly the matcher from the super class has also been
        // invoked.
        if (browserVersionCapabilityMatches) {
            return screenResolutionCapabilityMatches;
        } else {
            return super.matches(nodeCapability, requestedCapability) && screenResolutionCapabilityMatches;
        }
    }
}

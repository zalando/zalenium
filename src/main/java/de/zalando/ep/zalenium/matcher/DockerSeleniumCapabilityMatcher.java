package de.zalando.ep.zalenium.matcher;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
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
    private final Logger logger = Logger.getLogger(DockerSeleniumCapabilityMatcher.class.getName());
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
        boolean containsScreenResolutionCapability = false;
        // This validation is only done for docker-selenium nodes
        if (proxy instanceof DockerSeleniumRemoteProxy) {
            String[] screenResolutionNames = {"screenResolution", "resolution", "screen-resolution"};
            for (String screenResolutionName : screenResolutionNames) {
                if (requestedCapability.containsKey(screenResolutionName)) {
                    screenResolutionCapabilityMatches = nodeCapability.containsKey(screenResolutionName) &&
                            requestedCapability.get(screenResolutionName).equals(nodeCapability.get(screenResolutionName));
                    containsScreenResolutionCapability = true;
                }
            }
            // This is done to avoid having the test run on a node with a configured screen resolution different from
            // the global configured one. But not putting it to tests that should go to a cloud provider.
            if (!containsScreenResolutionCapability && super.matches(nodeCapability, requestedCapability)) {
                String screenResolution = String.format("%sx%s",
                        DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getWidth(),
                        DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getHeight());
                requestedCapability.put(screenResolutionNames[0], screenResolution);
            }
        }

        boolean timeZoneCapabilityMatches = true;
        boolean containsTimeZoneCapability = false;
        // This validation is only done for docker-selenium nodes
        if (proxy instanceof DockerSeleniumRemoteProxy) {
            String timeZoneName = "tz";
            if (requestedCapability.containsKey(timeZoneName)) {
                timeZoneCapabilityMatches = nodeCapability.containsKey(timeZoneName) &&
                        requestedCapability.get(timeZoneName).equals(nodeCapability.get(timeZoneName));
                containsTimeZoneCapability = true;
            }
            // This is done to avoid having the test run on a node with a configured time zone different from
            // the global configured one. But not putting it to tests that should go to a cloud provider.
            if (!containsTimeZoneCapability && super.matches(nodeCapability, requestedCapability)) {
                requestedCapability.put(timeZoneName, DockerSeleniumStarterRemoteProxy.getConfiguredTimeZone().getID());
            }
        }


            // If the browser version has been matched, then implicitly the matcher from the super class has also been
        // invoked.
        if (browserVersionCapabilityMatches) {
            return screenResolutionCapabilityMatches && timeZoneCapabilityMatches;
        } else {
            return super.matches(nodeCapability, requestedCapability) && screenResolutionCapabilityMatches &&
                    timeZoneCapabilityMatches;
        }
    }
}

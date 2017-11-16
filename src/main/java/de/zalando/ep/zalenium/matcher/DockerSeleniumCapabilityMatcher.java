package de.zalando.ep.zalenium.matcher;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        boolean screenResolutionMatches = true;
        boolean timeZoneCapabilityMatches = true;
        // This validation is only done for docker-selenium nodes
        if (proxy instanceof DockerSeleniumRemoteProxy) {
            screenResolutionMatches = isScreenResolutionMatching(nodeCapability, requestedCapability);
            timeZoneCapabilityMatches = isTimeZoneMatching(nodeCapability, requestedCapability);
        }

        return super.matches(nodeCapability, requestedCapability) && screenResolutionMatches &&
                timeZoneCapabilityMatches;
    }

    private boolean isScreenResolutionMatching(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        boolean screenResolutionCapabilityMatches = true;
        boolean screenSizeCapabilityIsRequested = false;

        String[] screenResolutionNames = {"screenResolution", "resolution", "screen-resolution"};
        for (String screenResolutionName : screenResolutionNames) {
            if (requestedCapability.containsKey(screenResolutionName)) {
                screenSizeCapabilityIsRequested = true;
                screenResolutionCapabilityMatches = nodeCapability.containsKey(screenResolutionName) &&
                        requestedCapability.get(screenResolutionName).equals(nodeCapability.get(screenResolutionName));
            }
        }

        /*
            This node has a screen size different from the default/configured one,
            and no special screen size was requested...
            then this validation prevents requests using nodes that were created with specific screen sizes
         */
        String defaultScreenResolution = String.format("%sx%s",
                DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getWidth(),
                DockerSeleniumStarterRemoteProxy.getConfiguredScreenSize().getHeight());
        String nodeScreenResolution = nodeCapability.get("screenResolution").toString();
        if (!screenSizeCapabilityIsRequested && !defaultScreenResolution.equalsIgnoreCase(nodeScreenResolution)) {
            screenResolutionCapabilityMatches = false;
        }
        return screenResolutionCapabilityMatches;
    }

    private boolean isTimeZoneMatching(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        boolean timeZoneCapabilityMatches;

        String timeZoneName = "tz";
        String defaultTimeZone = DockerSeleniumStarterRemoteProxy.getConfiguredTimeZone().getID();
        String nodeTimeZone = nodeCapability.get(timeZoneName).toString();

        /*
            If a time zone is not requested in the capabilities,
            and this node has a different time zone from the default/configured one...
            this will prevent that a request without a time zone uses a node created with a specific time zone
         */
        if (requestedCapability.containsKey(timeZoneName)) {
            timeZoneCapabilityMatches = nodeCapability.containsKey(timeZoneName) &&
                    requestedCapability.get(timeZoneName).equals(nodeCapability.get(timeZoneName));
        } else {
            timeZoneCapabilityMatches = defaultTimeZone.equalsIgnoreCase(nodeTimeZone);
        }

        return timeZoneCapabilityMatches;
    }

}

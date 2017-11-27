package de.zalando.ep.zalenium.matcher;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DockerSeleniumCapabilityMatcher extends DefaultCapabilityMatcher {
    private static String chromeVersion = null;
    private static String firefoxVersion = null;
    private static AtomicBoolean browserVersionsFetched = new AtomicBoolean(false);
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

        Map<String, Object> requestedCapabilityCopy = copyMap(requestedCapability);
        Map<String, Object> nodeCapabilityCopy = copyMap(nodeCapability);

        boolean screenResolutionMatches = true;
        boolean timeZoneCapabilityMatches = true;
        // This validation is only done for docker-selenium nodes
        if (proxy instanceof DockerSeleniumRemoteProxy) {
            getChromeAndFirefoxVersions(proxy);
            screenResolutionMatches = isScreenResolutionMatching(nodeCapabilityCopy, requestedCapabilityCopy);
            timeZoneCapabilityMatches = isTimeZoneMatching(nodeCapabilityCopy, requestedCapabilityCopy);
        } else if (proxy instanceof DockerSeleniumStarterRemoteProxy) {
            // We do this because the starter node does not have the browser versions
            if (browserVersionsFetched.get()) {
                String browser = nodeCapability.get(CapabilityType.BROWSER_NAME).toString();
                if (BrowserType.FIREFOX.equalsIgnoreCase(browser)) {
                    nodeCapabilityCopy.put(CapabilityType.VERSION, firefoxVersion);
                } else if (BrowserType.CHROME.equalsIgnoreCase(browser)) {
                    nodeCapabilityCopy.put(CapabilityType.VERSION, chromeVersion);
                }
            } else {
                requestedCapabilityCopy.remove(CapabilityType.VERSION);
            }
        }

        return super.matches(nodeCapabilityCopy, requestedCapabilityCopy) && screenResolutionMatches &&
                timeZoneCapabilityMatches;
    }

    // Cannot use Collectors.toMap() because it fails when there are null values.
    private Map<String, Object> copyMap(Map<String, Object> mapToCopy) {
        Map<String, Object> copiedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapToCopy.entrySet()) {
            copiedMap.put(entry.getKey(), entry.getValue());
        }
        return copiedMap;
    }

    private void getChromeAndFirefoxVersions(DefaultRemoteProxy proxy) {
        if (!browserVersionsFetched.getAndSet(true)) {
            for (TestSlot testSlot : proxy.getTestSlots()) {
                String browser = testSlot.getCapabilities().get(CapabilityType.BROWSER_NAME).toString();
                String browserVersion = testSlot.getCapabilities().get(CapabilityType.VERSION).toString();
                if (BrowserType.CHROME.equalsIgnoreCase(browser)) {
                    chromeVersion = browserVersion;
                } else if (BrowserType.FIREFOX.equalsIgnoreCase(browser)) {
                    firefoxVersion = browserVersion;
                }
            }
        }
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

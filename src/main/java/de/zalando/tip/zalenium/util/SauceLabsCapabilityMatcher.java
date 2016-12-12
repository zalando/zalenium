package de.zalando.tip.zalenium.util;

import de.zalando.tip.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The purpose of this class is to check if the capabilities cannot be supplied by docker-selenium so they can be just
 * forwarded to Sauce Labs
 */

public class SauceLabsCapabilityMatcher extends DefaultCapabilityMatcher {

    private static final Logger logger = Logger.getLogger(SauceLabsCapabilityMatcher.class.getName());

    private DefaultRemoteProxy proxy;

    public SauceLabsCapabilityMatcher(DefaultRemoteProxy defaultRemoteProxy) {
        super();
        proxy = defaultRemoteProxy;
    }

    @Override
    public boolean matches(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        logger.log(Level.FINE, String.format("Validating %s in node with capabilities %s", requestedCapability,
                nodeCapability));
        for (RemoteProxy remoteProxy : proxy.getRegistry().getAllProxies()) {
            if ((remoteProxy instanceof DockerSeleniumStarterRemoteProxy) &&
                    remoteProxy.hasCapability(requestedCapability)) {
                logger.log(Level.INFO, "[SL] Capability supported by docker-selenium, should not be processed by Sauce Labs: " +
                        "{0}", requestedCapability);
                return false;
            }
        }

        if (!requestedCapability.containsKey(CapabilityType.BROWSER_NAME)) {
            logger.log(Level.FINE, String.format("[SL] Capability %s does no contain %s key.", requestedCapability, CapabilityType.BROWSER_NAME));
            return false;
        }

        return true;
    }

}

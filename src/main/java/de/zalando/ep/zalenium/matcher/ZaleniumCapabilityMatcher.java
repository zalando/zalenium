package de.zalando.ep.zalenium.matcher;

import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The purpose of this class is to check if the capabilities cannot be supplied by docker-selenium so they can be just
 * forwarded to the Cloud Testing Provider
 */

public class ZaleniumCapabilityMatcher extends DefaultCapabilityMatcher {

    private static final Logger logger = Logger.getLogger(ZaleniumCapabilityMatcher.class.getName());

    private DefaultRemoteProxy proxy;

    public ZaleniumCapabilityMatcher(DefaultRemoteProxy defaultRemoteProxy) {
        super();
        proxy = defaultRemoteProxy;
    }

    @Override
    public boolean matches(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        logger.log(Level.FINE, ()-> String.format("Validating %s in node with capabilities %s", requestedCapability,
                nodeCapability));

        if (!requestedCapability.containsKey(CapabilityType.BROWSER_NAME)) {
            logger.log(Level.WARNING, () -> String.format("Capability %s does no contain %s key.", requestedCapability,
                    CapabilityType.BROWSER_NAME));
            return false;
        }

        for (RemoteProxy remoteProxy : proxy.getRegistry().getAllProxies()) {
            if ((remoteProxy instanceof DockerSeleniumStarterRemoteProxy) &&
                    remoteProxy.hasCapability(requestedCapability)) {
                logger.log(Level.FINE, "Capability supported by docker-selenium, should not be processed by " +
                        "a Cloud Testing Provider: {0}", requestedCapability);
                return false;
            }
        }

        return true;
    }

}

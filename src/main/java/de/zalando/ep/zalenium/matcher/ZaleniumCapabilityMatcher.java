package de.zalando.ep.zalenium.matcher;

import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The purpose of this class is to check if the capabilities cannot be supplied by docker-selenium so they can be just
 * forwarded to the Cloud Testing Provider
 */

public class ZaleniumCapabilityMatcher extends DefaultCapabilityMatcher {

    private static final Logger logger = LoggerFactory.getLogger(ZaleniumCapabilityMatcher.class.getName());

    private DefaultRemoteProxy proxy;

    public ZaleniumCapabilityMatcher(DefaultRemoteProxy defaultRemoteProxy) {
        super();
        proxy = defaultRemoteProxy;
    }

    @Override
    public boolean matches(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        logger.debug(String.format("Validating %s in node with capabilities %s", requestedCapability,
                nodeCapability));

        for (RemoteProxy remoteProxy : proxy.getRegistry().getAllProxies()) {
            if ((remoteProxy instanceof DockerSeleniumStarterRemoteProxy) &&
                    remoteProxy.hasCapability(requestedCapability)) {
                logger.debug(String.format("Capability supported by docker-selenium, should not be processed by " +
                        "a Cloud Testing Provider: %s", requestedCapability));
                return false;
            }
        }
        return true;
    }

}

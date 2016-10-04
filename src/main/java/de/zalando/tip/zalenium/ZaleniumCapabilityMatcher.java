package de.zalando.tip.zalenium;

import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.selenium.remote.CapabilityType;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Matches the requested capabilities vs. the available ones in docker-selenium and Sauce Labs.
 * Gives priority to check first docker-selenium, and then Sauce Labs.
 *
 */
@SuppressWarnings({"unused", "unchecked"})
// TODO: Evaluate if this class is needed or not after implementing the proxy for SauceLabs.
public class ZaleniumCapabilityMatcher extends DefaultCapabilityMatcher {

    private static final Logger LOG = Logger.getLogger(ZaleniumCapabilityMatcher.class.getName());

    private static final Map DOCKER_SELENIUM_FIREFOX_CAPABILITIES = new HashMap<String, Object>();
    static {
        DOCKER_SELENIUM_FIREFOX_CAPABILITIES.put(CapabilityType.BROWSER_NAME, "firefox");
        DOCKER_SELENIUM_FIREFOX_CAPABILITIES.put(CapabilityType.VERSION, "46.0.1");
        DOCKER_SELENIUM_FIREFOX_CAPABILITIES.put(CapabilityType.PLATFORM, "LINUX");
    }
    private static final Map DOCKER_SELENIUM_CHROME_CAPABILITIES = new HashMap<String, Object>();
    static {
        DOCKER_SELENIUM_CHROME_CAPABILITIES.put(CapabilityType.BROWSER_NAME, "chrome");
        DOCKER_SELENIUM_CHROME_CAPABILITIES.put(CapabilityType.VERSION, "51.0.2704.106");
        DOCKER_SELENIUM_CHROME_CAPABILITIES.put(CapabilityType.PLATFORM, "LINUX");
    }

    private static final String NODE_NAME_KEY = "nodeName";
    private static final String DOCKER_SELENIUM_STARTER_NODE_NAME = "docker-selenium-starter";
    private static final String SAUCE_LABS_NODE_NAME = "SauceLabs";

    @Override
    public boolean matches(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {

        /*
            Getting the nodeName for future operations
         */
        String nodeName = nodeCapability.getOrDefault(NODE_NAME_KEY, "").toString();

        /*
            If the node is 'docker-selenium', then return 'TRUE'
            This means that the node does not have 'nodeName' in its capabilities and that its
            capabilities match the requested ones.
         */
        if (nodeName.length() == 0 && super.matches(nodeCapability, requestedCapability)) {
            return true;
        }

        /*
            docker-selenium can match the requested capabilities, but no node has been launched, then
             we use the docker-selenium-starter
         */
        if (super.matches(DOCKER_SELENIUM_CHROME_CAPABILITIES, requestedCapability) ||
                super.matches(DOCKER_SELENIUM_FIREFOX_CAPABILITIES, requestedCapability)) {
            if (DOCKER_SELENIUM_STARTER_NODE_NAME.equalsIgnoreCase(nodeName)) {
                return true;
            }
        }

        /*
            Final option, if the node is Sauce Labs, it should match any requested capability
            TODO: Somehow update the available capabilities from Sauce Labs to improve the validation.
         */
        return SAUCE_LABS_NODE_NAME.equalsIgnoreCase(nodeName);

    }

}

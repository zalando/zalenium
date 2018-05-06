package de.zalando.ep.zalenium.matcher;

import java.util.Map;
import org.openqa.grid.internal.utils.DefaultCapabilityMatcher;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The purpose of this class is to check if the capabilities cannot be supplied by docker-selenium so they can be just
 * forwarded to the Cloud Testing Provider
 */

public class ZaleniumCapabilityMatcher extends DefaultCapabilityMatcher {

    private static final Logger logger = LoggerFactory.getLogger(ZaleniumCapabilityMatcher.class.getName());

    private DefaultCapabilityMatcher matcher;

    public ZaleniumCapabilityMatcher() {
        super();
        matcher = new DefaultCapabilityMatcher();
    }

    @Override
    public boolean matches(Map<String, Object> nodeCapability, Map<String, Object> requestedCapability) {
        logger.debug(String.format("Validating %s in node with capabilities %s", requestedCapability,
                nodeCapability));


        boolean matchesFirefox = matcher.matches(firefoxCapabilities().asMap(), requestedCapability);
        boolean matchesChrome = matcher.matches(chromeCapabilities().asMap(), requestedCapability);

        if (matchesChrome || matchesFirefox) {
            logger.debug(String.format("Capability supported by docker-selenium, should not be processed by " +
                "a Cloud Testing Provider: %s", requestedCapability));
            return false;
        }

        return true;
    }

    private MutableCapabilities firefoxCapabilities() {
        MutableCapabilities firefox = new MutableCapabilities();
        firefox.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        firefox.setCapability(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        if (DockerSeleniumCapabilityMatcher.getFirefoxVersion() != null) {
          firefox.setCapability(CapabilityType.VERSION, DockerSeleniumCapabilityMatcher.getFirefoxVersion());
        }
        return firefox;
    }

    private MutableCapabilities chromeCapabilities() {
        MutableCapabilities chrome = new MutableCapabilities();
        chrome.setCapability(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        chrome.setCapability(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        if (DockerSeleniumCapabilityMatcher.getChromeVersion() != null) {
          chrome.setCapability(CapabilityType.VERSION, DockerSeleniumCapabilityMatcher.getChromeVersion());
        }
        return chrome;
    }

}
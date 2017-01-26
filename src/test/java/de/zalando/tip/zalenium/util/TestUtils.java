package de.zalando.tip.zalenium.util;

import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.utils.configuration.GridNodeConfiguration;
import com.beust.jcommander.JCommander;
import com.google.gson.JsonObject;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Collections;
import java.util.Enumeration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TestUtils {

    public static RegistrationRequest getRegistrationRequestForTesting(final int port, String proxyClass) {
        GridNodeConfiguration nodeConfiguration = new GridNodeConfiguration();
        new JCommander(nodeConfiguration, "-role", "wd", "-hubHost", "localhost", "-hubPort", "4444",
                "-host","localhost", "-port", String.valueOf(port), "-proxy", proxyClass, "-registerCycle", "5000",
                "-maxSession", "5");

        return RegistrationRequest.build(nodeConfiguration);
    }

    public static WebDriverRequest getMockedWebDriverRequestStartSession() {
        WebDriverRequest request = mock(WebDriverRequest.class);
        when(request.getRequestURI()).thenReturn("session");
        when(request.getServletPath()).thenReturn("session");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestType()).thenReturn(RequestType.START_SESSION);
        JsonObject jsonObject = new JsonObject();
        JsonObject desiredCapabilities = new JsonObject();
        desiredCapabilities.addProperty(CapabilityType.BROWSER_NAME, BrowserType.IE);
        desiredCapabilities.addProperty(CapabilityType.PLATFORM, Platform.WIN8.name());
        jsonObject.add("desiredCapabilities", desiredCapabilities);
        when(request.getBody()).thenReturn(jsonObject.toString());

        Enumeration<String> strings = Collections.emptyEnumeration();
        when(request.getHeaderNames()).thenReturn(strings);

        return request;
    }

}

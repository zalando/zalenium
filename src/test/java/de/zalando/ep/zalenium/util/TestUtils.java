package de.zalando.ep.zalenium.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockeredSeleniumStarter;
import org.apache.commons.io.FileUtils;
import org.junit.rules.TemporaryFolder;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.utils.configuration.GridNodeConfiguration;
import com.beust.jcommander.JCommander;
import org.openqa.grid.web.servlet.handler.RequestHandler;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.SeleniumBasedRequest;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;


public class TestUtils {

    public static RegistrationRequest getRegistrationRequestForTesting(final int port, String proxyClass) {
        GridNodeConfiguration nodeConfiguration = new GridNodeConfiguration();
        nodeConfiguration.role = "wd";
        nodeConfiguration.hub = "http://localhost:4444";
        nodeConfiguration.host = "localhost";
        nodeConfiguration.port = port;
        nodeConfiguration.proxy = proxyClass;
        nodeConfiguration.registerCycle = 5000;
        nodeConfiguration.cleanUpCycle = 5000;
        nodeConfiguration.maxSession = 5;
        new JCommander(nodeConfiguration);
        return RegistrationRequest.build(nodeConfiguration);
    }

    public static WebDriverRequest getMockedWebDriverRequestStartSession(String browser, Platform platform) {
        WebDriverRequest request = mock(WebDriverRequest.class);
        when(request.getRequestURI()).thenReturn("session");
        when(request.getServletPath()).thenReturn("session");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestType()).thenReturn(RequestType.START_SESSION);
        JsonObject jsonObject = new JsonObject();
        JsonObject desiredCapabilities = new JsonObject();
        desiredCapabilities.addProperty(CapabilityType.BROWSER_NAME, browser);
        desiredCapabilities.addProperty(CapabilityType.PLATFORM_NAME, platform.name());
        jsonObject.add("desiredCapabilities", desiredCapabilities);
        when(request.getBody()).thenReturn(jsonObject.toString());

        Enumeration<String> strings = Collections.emptyEnumeration();
        when(request.getHeaderNames()).thenReturn(strings);

        return request;
    }

    public static List<MutableCapabilities> getDockerSeleniumCapabilitiesForTesting() {
        String screenResolution = String.format("%sx%s",
                DockeredSeleniumStarter.getConfiguredScreenSize().getWidth(),
                DockeredSeleniumStarter.getConfiguredScreenSize().getHeight());
        List<MutableCapabilities> dsCapabilities = new ArrayList<>();
        MutableCapabilities firefoxCapabilities = new MutableCapabilities();
        firefoxCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        firefoxCapabilities.setCapability(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        firefoxCapabilities.setCapability(CapabilityType.VERSION, "57.0");
        firefoxCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        firefoxCapabilities.setCapability("screenResolution", screenResolution);
        firefoxCapabilities.setCapability("tz", DockeredSeleniumStarter.getConfiguredTimeZone().getID());
        dsCapabilities.add(firefoxCapabilities);
        MutableCapabilities chromeCapabilities = new MutableCapabilities();
        chromeCapabilities.setCapability(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        chromeCapabilities.setCapability(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        chromeCapabilities.setCapability(CapabilityType.VERSION, "62.0.3202.94");
        chromeCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        chromeCapabilities.setCapability("screenResolution", screenResolution);
        chromeCapabilities.setCapability("tz", DockeredSeleniumStarter.getConfiguredTimeZone().getID());
        dsCapabilities.add(chromeCapabilities);
        return dsCapabilities;
    }

    @SuppressWarnings("ConstantConditions")
    public static JsonElement getTestInformationSample(String fileName) throws IOException {
        URL testInfoLocation = TestUtils.class.getClassLoader().getResource(fileName);
        File testInformationFile = new File(testInfoLocation.getPath());
        String testInformation = FileUtils.readFileToString(testInformationFile, StandardCharsets.UTF_8);
        return new JsonParser().parse(testInformation);
    }

    public static ServletOutputStream getMockedServletOutputStream() {
        return new ServletOutputStream() {
            private StringBuilder stringBuilder = new StringBuilder();

            @Override
            public boolean isReady() {
                System.out.println("isReady");
                return false;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                System.out.println("setWriteListener");
            }

            @Override
            public void write(int b) {
                this.stringBuilder.append((char) b );
            }

            public String toString() {
                return stringBuilder.toString();
            }
        };
    }

    public static CommonProxyUtilities mockCommonProxyUtilitiesForDashboardTesting(TemporaryFolder temporaryFolder) {
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.currentLocalPath()).thenReturn(temporaryFolder.getRoot().getAbsolutePath());
        when(commonProxyUtilities.getShortDateAndTime(any(Date.class))).thenCallRealMethod();
        when(commonProxyUtilities.getDateAndTimeFormatted(any(Date.class))).thenCallRealMethod();
        when(commonProxyUtilities.getDateAndTime(any(Date.class), any(Integer.class))).thenCallRealMethod();
        return commonProxyUtilities;
    }

    public static void ensureRequiredInputFilesExist(TemporaryFolder temporaryFolder) throws IOException {
        temporaryFolder.newFile("list_template.html");
        temporaryFolder.newFile("dashboard_template.html");
        temporaryFolder.newFile("zalando.ico");
        temporaryFolder.newFolder("css");
        temporaryFolder.newFolder("js");
    }

    public static void ensureRequiredFilesExistForCleanup(TemporaryFolder temporaryFolder) throws IOException {
        ensureRequiredInputFilesExist(temporaryFolder);
        temporaryFolder.newFolder("videos");
        temporaryFolder.newFolder("videos", "logs");
        temporaryFolder.newFile("videos/list.html");
        temporaryFolder.newFile("videos/executedTestsInfo.json");
        temporaryFolder.newFile("videos/dashboard.html");
    }

    public static DockerSeleniumRemoteProxy getNewBasicRemoteProxy(String browser, String url, GridRegistry registry) throws MalformedURLException {

        GridNodeConfiguration config = new GridNodeConfiguration();
        URL u = new URL(url);
        config.host = u.getHost();
        config.port = u.getPort();
        config.role = "webdriver";
        RegistrationRequest req = RegistrationRequest.build(config);
        req.getConfiguration().capabilities.clear();

        DesiredCapabilities capability = new DesiredCapabilities();
        capability.setBrowserName(browser);
        req.getConfiguration().capabilities.add(capability);

        return createProxy(registry, req);

    }

    private static DockerSeleniumRemoteProxy createProxy(GridRegistry registry, RegistrationRequest req) {
      return new DockerSeleniumRemoteProxy(req, registry);
    }

    public static RequestHandler createNewSessionHandler(GridRegistry registry,
                                                         Map<String, Object> desiredCapability) {
        SeleniumBasedRequest request = mock(SeleniumBasedRequest.class);
        when(request.getDesiredCapabilities()).thenReturn(desiredCapability);
        when(request.getRequestType()).thenReturn(RequestType.START_SESSION);
        when(request.getCreationTime()).thenReturn(System.currentTimeMillis());
        HttpServletResponse response = mock(HttpServletResponse.class);
        return new MockedRequestHandler(request, response, registry);
    }
}

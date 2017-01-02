package de.zalando.tip.zalenium.proxy;


import com.google.gson.JsonObject;
import de.zalando.tip.zalenium.util.CommonProxyUtilities;
import de.zalando.tip.zalenium.util.Environment;
import de.zalando.tip.zalenium.util.GoogleAnalyticsApi;
import de.zalando.tip.zalenium.util.TestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.mockito.Mockito.*;

public class TestingBotRemoteProxyTest {

    private TestingBotRemoteProxy testingBotProxy;
    private Registry registry;

    @SuppressWarnings("ConstantConditions")
    @Before
    public void setUp() {
        registry = Registry.newInstance();
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30002,
                TestingBotRemoteProxy.class.getCanonicalName());
        URL resource = TestingBotRemoteProxyTest.class.getClassLoader().getResource("testingbot_capabilities.json");
        File fileLocation = new File(resource.getPath());
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(anyString())).thenReturn(null);
        when(commonProxyUtilities.readJSONFromFile(anyString())).thenCallRealMethod();
        when(commonProxyUtilities.currentLocalPath()).thenReturn(fileLocation.getParent());
        TestingBotRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        testingBotProxy = TestingBotRemoteProxy.getNewInstance(request, registry);

        // we need to register a DockerSeleniumStarter proxy to have a proper functioning testingBotProxy
        request = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
        DockerSeleniumStarterRemoteProxy dsStarterProxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        // We add both nodes to the registry
        registry.add(testingBotProxy);
        registry.add(dsStarterProxy);
    }

    @After
    public void tearDown() {
        TestingBotRemoteProxy.restoreCommonProxyUtilities();
        TestingBotRemoteProxy.restoreGa();
        TestingBotRemoteProxy.restoreEnvironment();
    }

    @Test
    public void checkProxyOrdering() {
        // Checking that the DockerSeleniumStarterProxy should come before SauceLabsProxy
        List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
        Assert.assertEquals(2, sorted.size());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.class, sorted.get(0).getClass());
        Assert.assertEquals(TestingBotRemoteProxy.class, sorted.get(1).getClass());
    }

    @Test
    public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN8);

        TestSession testSession = testingBotProxy.getNewSession(requestedCapability);

        Assert.assertNotNull(testSession);
    }
}

package de.zalando.tip.zalenium.proxy;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.grid.common.GridRole;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SauceLabsRemoteProxyTest {

    private static SauceLabsRemoteProxy sauceLabsProxy;
    private static Registry registry;


    @BeforeClass
    public static void setup() {
        registry = Registry.newInstance();
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = new RegistrationRequest();
        request.setRole(GridRole.NODE);
        request.getConfiguration().put(RegistrationRequest.MAX_SESSION, 5);
        request.getConfiguration().put(RegistrationRequest.AUTO_REGISTER, true);
        request.getConfiguration().put(RegistrationRequest.REGISTER_CYCLE, 5000);
        request.getConfiguration().put(RegistrationRequest.HUB_HOST, "localhost");
        request.getConfiguration().put(RegistrationRequest.HUB_PORT, 4444);
        request.getConfiguration().put(RegistrationRequest.PORT, 30001);
        request.getConfiguration().put(RegistrationRequest.PROXY_CLASS, "de.zalando.tip.zalenium.proxy.SauceLabsRemoteProxy");
        request.getConfiguration().put(RegistrationRequest.REMOTE_HOST, "http://localhost:4444");

        sauceLabsProxy = SauceLabsRemoteProxy.getNewInstance(request, registry);

        // we need to register a DockerSeleniumStarter proxy to have a proper functioning SauceLabsProxy
        request = new RegistrationRequest();
        request.setRole(GridRole.NODE);
        request.getConfiguration().put(RegistrationRequest.MAX_SESSION, 5);
        request.getConfiguration().put(RegistrationRequest.AUTO_REGISTER, true);
        request.getConfiguration().put(RegistrationRequest.REGISTER_CYCLE, 5000);
        request.getConfiguration().put(RegistrationRequest.HUB_HOST, "localhost");
        request.getConfiguration().put(RegistrationRequest.HUB_PORT, 4444);
        request.getConfiguration().put(RegistrationRequest.PORT, 30002);
        request.getConfiguration().put(RegistrationRequest.PROXY_CLASS, "de.zalando.tip.zalenium.proxy.DockerSeleniumStarterRemoteProxy");
        request.getConfiguration().put(RegistrationRequest.REMOTE_HOST, "http://localhost:4444");

        DockerSeleniumStarterRemoteProxy dockerSeleniumStarterProxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        // We add both nodes to the registry
        registry.add(sauceLabsProxy);
        registry.add(dockerSeleniumStarterProxy);
    }

    @Test
    public void doesNotCreateSessionWhenDockerSeleniumCanProcessRequest() {
        // This capability is supported by docker-selenium, so it should return a null session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);

        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);

        Assert.assertNull(testSession);
    }

    @Test
    public void unknownCapabilityDoesNotCreateSession() {
        // Non existent capability that should not create a session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, "RANDOM_NOT_EXISTENT_BROWSER");
        requestedCapability.put(CapabilityType.PLATFORM, Platform.ANY);

        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);

        Assert.assertNull(testSession);
    }

    @Test
    public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.MAC);

        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);

        Assert.assertNotNull(testSession);
    }

    @Test
    public void checkProxyOrdering() {
        // Checking that the DockerSeleniumStarterProxy should come before SauceLabsProxy
        List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
        Assert.assertEquals(2, sorted.size());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.class, sorted.get(0).getClass());
        Assert.assertEquals(SauceLabsRemoteProxy.class, sorted.get(1).getClass());
    }

}

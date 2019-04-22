package de.zalando.ep.zalenium.servlet;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import de.zalando.ep.zalenium.util.TestUtils;
import static org.awaitility.Awaitility.await;
import org.awaitility.Duration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.Hub;
import org.openqa.grid.web.servlet.handler.RequestHandler;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public class SessionsServletTest {

    private SessionsServlet sessionsServlet;
    private ZaleniumRegistry registry;

    @Before
    public void initMocksAndService() {
        registry = (ZaleniumRegistry) ZaleniumRegistry.newInstance(new Hub(new GridHubConfiguration()), new ProxySet(false));
        sessionsServlet = new SessionsServlet(registry);
    }

    @Test
    public void sessionIsCleaning() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);

        RegistrationRequest req = TestUtils.getRegistrationRequestForTesting(40000, DockerSeleniumRemoteProxy.class.getCanonicalName());
        req.getConfiguration().capabilities.clear();
        req.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());
        DockerSeleniumRemoteProxy p1 = new DockerSeleniumRemoteProxy(req, registry);

        try {
            registry.add(p1);

            await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.TWO_SECONDS).until(() -> registry.getAllProxies().size() == 1);

            RequestHandler newSessionRequest = TestUtils.createNewSessionHandler(registry, requestedCapability);
            newSessionRequest.process();
            TestSession session = newSessionRequest.getSession();
            session.setExternalKey(new ExternalSessionKey(UUID.randomUUID().toString()));

            // Call servlet action to clean all active session
            ActionsServlet.ResponseAction responseAction = sessionsServlet.doAction("doCleanupActiveSessions");
            Assert.assertNotNull(responseAction);
            Assert.assertEquals("SUCCESS", responseAction.getResultMsg());
            Assert.assertEquals(200, responseAction.getResponseStatus());

            Callable<Boolean> callable = () -> registry.getActiveSessions().size() == 0;
            await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.TWO_SECONDS).until(callable);

        } finally {
            registry.stop();
        }
    }

    @Test
    public void missingParameter(){
        ActionsServlet.ResponseAction responseAction = sessionsServlet.doAction(null);
        Assert.assertNotNull(responseAction);
        Assert.assertEquals(400, responseAction.getResponseStatus());
        Assert.assertEquals("ERROR action not implemented. Given action=null", responseAction.getResultMsg());
    }

    @Test
    public void unsupportedParameter() {
        ActionsServlet.ResponseAction responseAction = sessionsServlet.doAction("anyValue");
        Assert.assertNotNull(responseAction);
        Assert.assertEquals(400, responseAction.getResponseStatus());
        Assert.assertEquals("ERROR action not implemented. Given action=anyValue",
                responseAction.getResultMsg());
    }
}

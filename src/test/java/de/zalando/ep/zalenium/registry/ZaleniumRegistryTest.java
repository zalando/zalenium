package de.zalando.ep.zalenium.registry;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.util.TestUtils;
import org.awaitility.Duration;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.SessionTerminationReason;
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

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertTrue;

public class ZaleniumRegistryTest {

    @Test
    public void proxyIsAdded() throws Exception {
        GridRegistry registry = ZaleniumRegistry.newInstance(new Hub(new GridHubConfiguration()));
        DockerSeleniumRemoteProxy p1 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine1:4444/", registry);
        DockerSeleniumRemoteProxy p2 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine2:4444/", registry);
        DockerSeleniumRemoteProxy p3 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine3:4444/", registry);
        DockerSeleniumRemoteProxy p4 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine4:4444/", registry);
        try {
            registry.add(p1);
            registry.add(p2);
            registry.add(p3);
            registry.add(p4);
            assertTrue(registry.getAllProxies().size() == 4);
        } finally {
            registry.stop();
        }
    }

    @Test
    public void proxyIsRemoved() throws Exception {
        GridRegistry registry = ZaleniumRegistry.newInstance(new Hub(new GridHubConfiguration()));
        DockerSeleniumRemoteProxy p1 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine1:4444/", registry);
        try {
            registry.add(p1);
            assertTrue(registry.getAllProxies().size() == 1);
            registry.removeIfPresent(p1);
            assertTrue(registry.getAllProxies().size() == 0);
        } finally {
            registry.stop();
        }
    }

    @Test
    public void sessionIsProcessed() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);

        GridRegistry registry = ZaleniumRegistry.newInstance(new Hub(new GridHubConfiguration()));
        RegistrationRequest req = TestUtils.getRegistrationRequestForTesting(40000, DockerSeleniumRemoteProxy.class.getCanonicalName());
        req.getConfiguration().capabilities.clear();
        req.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());
        DockerSeleniumRemoteProxy p1 = new DockerSeleniumRemoteProxy(req, registry);

        try {
            registry.add(p1);

            RequestHandler newSessionRequest = TestUtils.createNewSessionHandler(registry, requestedCapability);
            newSessionRequest.process();
            TestSession session = newSessionRequest.getSession();
            session.setExternalKey(new ExternalSessionKey(UUID.randomUUID().toString()));

            registry.terminate(session, SessionTerminationReason.CLIENT_STOPPED_SESSION);
            Callable<Boolean> callable = () -> registry.getActiveSessions().size() == 0;
            await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.TWO_SECONDS).until(callable);

        } finally {
            registry.stop();
        }
    }
}
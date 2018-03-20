package de.zalando.ep.zalenium.registry;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.util.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.Hub;

import static org.junit.Assert.*;

public class ZaleniumRegistryTest {

    @Before
    public void setUp() {
    }

    @Test
    public void addProxy() throws Exception {
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

    @After
    public void tearDown() {
    }
}
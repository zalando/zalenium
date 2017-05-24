package de.zalando.ep.zalenium.servlet;


import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import de.zalando.ep.zalenium.util.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSlot;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CopyNodeServletTest {

    private Registry registry;
    private HttpServletResponse response;
    private boolean called;

    @Before
    public void setUp() throws IOException {
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("test", "test");

        TestSlot testSlot = mock(TestSlot.class);
        when(testSlot.getCapabilities()).thenReturn(capabilities);

        DockerSeleniumRemoteProxy dockerProxy = mock(DockerSeleniumRemoteProxy.class);
        LinkedList<TestSlot> testSlots = new LinkedList<>();
        testSlots.add(testSlot);
        when(dockerProxy.getTestSlots()).thenReturn(testSlots);

        registry = mock(Registry.class);
        when(registry.getProxyById("http://localhost:40000")).thenReturn(dockerProxy);

        DockerSeleniumStarterRemoteProxy starterRemoteProxy = mock(DockerSeleniumStarterRemoteProxy.class);
        when(starterRemoteProxy.getNewSession(capabilities)).thenAnswer(invocationOnMock -> {
            called = true;
            return null;
        });

        ProxySet proxies = new ProxySet(false);
        proxies.add(starterRemoteProxy);
        proxies.add(dockerProxy);
        when(registry.getAllProxies()).thenReturn(proxies);


        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());
    }

    @Test(dataProvider = "copyProxyDataProvider")
    public void copyProxy(String usedId, boolean isCreatedCalled) throws ServletException, IOException {

        CopyNodeServlet servlet = new CopyNodeServlet(registry);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("id")).thenReturn(usedId);

        called = false;
        servlet.doPost(request, response);

        String responseContent = response.getOutputStream().toString();
        assertThat(responseContent, containsString("ok"));
        Assert.assertEquals(isCreatedCalled, called);
    }

    @DataProvider
    public Object[][] copyProxyDataProvider() {
        return new Object[][]{
                {"http://localhost:40000", true},
                {"http://not-existing:40000", false}
        };
    }

}

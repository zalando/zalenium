package de.zalando.ep.zalenium.dashboard;

import de.zalando.ep.zalenium.servlet.ActionsServlet;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;


public class DashboardCleanupServletTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private DashboardCleanupServlet dashboardCleanupServlet;

    @Before
    public void initMocksAndService() throws IOException {
        dashboardCleanupServlet = new DashboardCleanupServlet();
        // Temporal folder for dashboard files
        TestUtils.ensureRequiredFilesExistForCleanup(temporaryFolder);
    }

    @Test
    public void doCleanup() {
        try {
            CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            Dashboard.setCommonProxyUtilities(proxyUtilities);
            ActionsServlet.ResponseAction responseAction = dashboardCleanupServlet.doAction("doCleanup");
            Assert.assertNotNull(responseAction);
            Assert.assertEquals("SUCCESS", responseAction.getResultMsg());
            Assert.assertEquals(200, responseAction.getResponseStatus());
        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }

    @Test
    public void doReset() {
        try {
            CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            Dashboard.setCommonProxyUtilities(proxyUtilities);
            ActionsServlet.ResponseAction responseAction = dashboardCleanupServlet.doAction("doReset");
            Assert.assertNotNull(responseAction);
            Assert.assertEquals("SUCCESS", responseAction.getResultMsg());
            Assert.assertEquals(200, responseAction.getResponseStatus());
        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }

    @Test
    public void missingParameter(){
        ActionsServlet.ResponseAction responseAction = dashboardCleanupServlet.doAction(null);
        Assert.assertNotNull(responseAction);
        Assert.assertEquals(400, responseAction.getResponseStatus());
        Assert.assertEquals("ERROR action not implemented. Given action=null", responseAction.getResultMsg());
    }

    @Test
    public void unsupportedParameter() {
        ActionsServlet.ResponseAction responseAction = dashboardCleanupServlet.doAction("anyValue");
        Assert.assertNotNull(responseAction);
        Assert.assertEquals(400, responseAction.getResponseStatus());
        Assert.assertEquals("ERROR action not implemented. Given action=anyValue",
                responseAction.getResultMsg());
    }
}

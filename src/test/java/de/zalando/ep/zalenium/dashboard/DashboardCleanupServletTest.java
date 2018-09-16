package de.zalando.ep.zalenium.dashboard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DashboardCleanupServletTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private HttpServletRequest request;
    private HttpServletResponse response;
    private DashboardCleanupServlet dashboardCleanupServlet;

    @Before
    public void initMocksAndService() throws IOException {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
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
                this.stringBuilder.append((char) b);
            }

            public String toString() {
                return stringBuilder.toString();
            }
        });
        dashboardCleanupServlet = new DashboardCleanupServlet();
        // Temporal folder for dashboard files
        TestUtils.ensureRequiredFilesExistForCleanup(temporaryFolder);
    }

    @Test
    public void getDoCleanup() throws IOException {
        try {
            CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            Dashboard.setCommonProxyUtilities(proxyUtilities);
            when(request.getParameter("action")).thenReturn("doCleanup");
            dashboardCleanupServlet.doGet(request, response);
            Assert.assertEquals("SUCCESS", response.getOutputStream().toString());

        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }

    @Test
    public void postDoCleanup() throws IOException {
        try {
            CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            Dashboard.setCommonProxyUtilities(proxyUtilities);
            when(request.getParameter("action")).thenReturn("doCleanup");
            dashboardCleanupServlet.doPost(request, response);
            Assert.assertEquals("SUCCESS", response.getOutputStream().toString());
        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }
    
    @Test
    public void getDoReset() throws IOException {
        try {
            CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            Dashboard.setCommonProxyUtilities(proxyUtilities);
            when(request.getParameter("action")).thenReturn("doReset");
            dashboardCleanupServlet.doGet(request, response);
            Assert.assertEquals("SUCCESS", response.getOutputStream().toString());

        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }
    
    @Test
    public void postDoReset() throws IOException {
        try {
            CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            Dashboard.setCommonProxyUtilities(proxyUtilities);
            when(request.getParameter("action")).thenReturn("doReset");
            dashboardCleanupServlet.doPost(request, response);
            Assert.assertEquals("SUCCESS", response.getOutputStream().toString());
        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }


    @Test
    public void postMissingParameter() throws IOException {
        dashboardCleanupServlet.doPost(request, response);
        Assert.assertEquals("ERROR action not implemented. Given action=null", response.getOutputStream().toString());
    }

    @Test
    public void postUnsupportedParameter() throws IOException {
        when(request.getParameter("action")).thenReturn("anyValue");
        dashboardCleanupServlet.doPost(request, response);
        Assert.assertEquals("ERROR action not implemented. Given action=anyValue",
                response.getOutputStream().toString());
    }
}

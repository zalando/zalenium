package de.zalando.ep.zalenium.dashboard;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DashboardInformationServletTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private HttpServletRequest request;
    private HttpServletResponse response;
    private DashboardInformationServlet dashboardInformationServlet;

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
        dashboardInformationServlet = new DashboardInformationServlet();
    }

    @Test
    public void getReturnsEmptyArray() throws IOException {
        try {
            dashboardInformationServlet.doGet(request, response);
            Assert.assertEquals("[]", response.getOutputStream().toString());

        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }

}

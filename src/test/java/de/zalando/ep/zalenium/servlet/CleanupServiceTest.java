package de.zalando.ep.zalenium.servlet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.zalando.ep.zalenium.util.DashboardTempFileBase;

public class CleanupServiceTest extends DashboardTempFileBase {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private CleanupService cleanupService;

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
            public void write(int b) throws IOException {
                this.stringBuilder.append((char) b);
            }

            public String toString() {
                return stringBuilder.toString();
            }
        });
        cleanupService = new CleanupService();
    }

    @Test
    public void getRequestsNotAllowed() throws ServletException, IOException {
        cleanupService.doGet(request, response);
        Assert.assertEquals("ERROR GET request not implemented", response.getOutputStream().toString());
    }

    @Test
    public void postMissingParameter() throws ServletException, IOException {
        cleanupService.doPost(request, response);
        Assert.assertEquals("ERROR action not implemented. Given action=null", response.getOutputStream().toString());
    }

    @Test
    public void postUnsupportedParameter() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("anyValue");
        cleanupService.doPost(request, response);
        Assert.assertEquals("ERROR action not implemented. Given action=anyValue",
                response.getOutputStream().toString());
    }

    @Test
    public void postDoCleanupAll() throws ServletException, IOException {
        when(request.getParameter("action")).thenReturn("doCleanupAll");
        cleanupService.doPost(request, response);
        Assert.assertEquals("SUCCESS", response.getOutputStream().toString());
    }
}

package de.zalando.ep.zalenium.servlet;

import de.zalando.ep.zalenium.util.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ActionsServletTest {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private ActionsServlet actionsServlet;

    @Before
    public void initMocksAndService() throws IOException {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());
        actionsServlet = new ActionsServlet() {
            @Override
            protected ResponseAction doAction(String action) {
                return new ResponseAction("SUCCESS", 200);
            }
        };
    }

    @Test
    public void doPostAction() throws IOException {
        when(request.getParameter("action")).thenReturn("anyValue");
        actionsServlet.doPost(request, response);
        Assert.assertEquals("SUCCESS",
                response.getOutputStream().toString());
    }

    @Test
    public void doGetAction() throws IOException {
        when(request.getParameter("action")).thenReturn("anyValue");
        actionsServlet.doGet(request, response);
        Assert.assertEquals("SUCCESS",
                response.getOutputStream().toString());
    }
}

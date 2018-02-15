package de.zalando.ep.zalenium.dashboard;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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
    public void getReturnsCurrentTestInformationCount() throws IOException {
        try {
            dashboardInformationServlet.doGet(request, response);
            String responseContents = response.getOutputStream().toString();
            Type collectionType = new TypeToken<ArrayList<TestInformation>>(){}.getType();
            List<TestInformation> testInformationList = new Gson().fromJson(responseContents, collectionType);
            Assert.assertEquals(testInformationList.size(), Dashboard.getExecutedTestsInformation().size());

        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }

}

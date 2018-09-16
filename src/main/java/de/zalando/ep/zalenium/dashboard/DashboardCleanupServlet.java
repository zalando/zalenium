package de.zalando.ep.zalenium.dashboard;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import com.google.common.io.ByteStreams;

@SuppressWarnings("WeakerAccess")
public class DashboardCleanupServlet extends RegistryBasedServlet {

    private static final String DO_RESET = "doReset";
    private static final String DO_CLEANUP = "doCleanup";

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardCleanupServlet.class.getName());

    @SuppressWarnings("unused")
    public DashboardCleanupServlet() {
        this(null);
    }

    public DashboardCleanupServlet(ZaleniumRegistry registry) {
        super(registry);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }

    protected void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = "";
        try {
            action = request.getParameter("action");
        } catch (Exception e) {
            LOGGER.debug(e.toString(), e);
        }

        String resultMsg;
        int responseStatus;
        if (DO_RESET.equals(action)) {
            DashboardCollection.resetDashboard();
            resultMsg = "SUCCESS";
            responseStatus = 200;
        } else if(DO_CLEANUP.equals(action)) {
            DashboardCollection.cleanupDashboard();
            resultMsg = "SUCCESS";
            responseStatus = 200;
        }
        else {
            resultMsg = "ERROR action not implemented. Given action=" + action;
            responseStatus = 400;
        }
        sendMessage(response, resultMsg, responseStatus);
    }

    private void sendMessage(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);

        try (InputStream in = new ByteArrayInputStream(message.getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }
}

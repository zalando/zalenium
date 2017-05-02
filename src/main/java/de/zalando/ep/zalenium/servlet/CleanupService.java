package de.zalando.ep.zalenium.servlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openqa.grid.internal.Registry;
import org.openqa.grid.web.servlet.RegistryBasedServlet;

import com.google.common.io.ByteStreams;

import de.zalando.ep.zalenium.util.DashboardDataHandler;
import de.zalando.ep.zalenium.util.TestInformation;

public class CleanupService extends RegistryBasedServlet {

    public static final String DO_CLEANUP_ALL = "doCleanupAll";

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(CleanupService.class.getName());

    private static final String NOT_IMPORTANT = "notImportant";
    private static TestInformation dashboardPathAccess = new TestInformation(NOT_IMPORTANT, NOT_IMPORTANT,
            NOT_IMPORTANT, NOT_IMPORTANT, NOT_IMPORTANT, NOT_IMPORTANT);

    @SuppressWarnings("unused")
    public CleanupService() {
        this(null);
    }

    public CleanupService(Registry registry) {
        super(registry);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            sendMessage(response, "ERROR GET request not implemented", 400);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString(), e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            process(request, response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString(), e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = "";
        try {
            action = request.getParameter("action");
        } catch (Exception e) {
            LOGGER.log(Level.FINE, e.toString(), e);
        }

        String resultMsg;
        int responseStatus;
        if (DO_CLEANUP_ALL.equals(action)) {
            String dashboardPath = dashboardPathAccess.getVideoFolderPath();
            DashboardDataHandler.clearRecordedVideosAndLogs(dashboardPath);
            resultMsg = "SUCCESS";
            responseStatus = 200;
        } else {
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

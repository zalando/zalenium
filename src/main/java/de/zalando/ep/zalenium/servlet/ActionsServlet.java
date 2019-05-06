package de.zalando.ep.zalenium.servlet;

import com.google.common.io.ByteStreams;
import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import org.apache.commons.lang3.StringUtils;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class ActionsServlet extends RegistryBasedServlet {

    protected static final int RESPONSE_STATUS_400 = 400;
    protected static final String ERROR_ACTION_NOT_IMPLEMENTED_GIVEN_ACTION = "ERROR action not implemented. Given action=";
    protected final ResponseAction successResponseAction = new ResponseAction("SUCCESS", 200);

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionsServlet.class.getName());

    @SuppressWarnings("unused")
    public ActionsServlet() {
        this(null);
    }

    public ActionsServlet(ZaleniumRegistry registry) {
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

    public class ResponseAction {
        private String resultMsg;
        private int responseStatus;

        public ResponseAction(String resultMsg, int responseStatus) {
            this.resultMsg = resultMsg;
            this.responseStatus = responseStatus;
        }

        public String getResultMsg() {
            return resultMsg;
        }

        public int getResponseStatus() {
            return responseStatus;
        }
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = StringUtils.EMPTY;
        try {
            action = request.getParameter("action");
        } catch (Exception e) {
            LOGGER.debug(e.toString(), e);
        }
        sendMessage(response, doAction(action));
    }

    protected abstract ResponseAction doAction(final String action);

    private void sendMessage(HttpServletResponse response, ResponseAction responseAction) throws IOException {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(responseAction.getResponseStatus());

        try (InputStream in = new ByteArrayInputStream(responseAction.getResultMsg().getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }
}

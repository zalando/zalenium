package de.zalando.ep.zalenium.servlet;

import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import org.openqa.grid.internal.SessionTerminationReason;
import org.openqa.grid.internal.TestSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet allowing to manipulate the sessions of the selenium grid.
 */
public class SessionsServlet extends ActionsServlet {

    private static final String DO_CLEANUP_ACTIVE_SESSIONS = "doCleanupActiveSessions";

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionsServlet.class.getName());

    @SuppressWarnings("unused")
    public SessionsServlet() {
        this(null);
    }

    public SessionsServlet(ZaleniumRegistry registry) {
        super(registry);
    }

    @Override
    protected ResponseAction doAction(final String action) {
        if (DO_CLEANUP_ACTIVE_SESSIONS.equals(action)) {
            for (TestSession testSession : getRegistry().getActiveSessions()) {
                LOGGER.info("Terminate active session (ext key : {}, int key : {}) in slot {}.",
                        testSession.getSlot().getSession().getExternalKey(),
                        testSession.getSlot().getSession().getInternalKey(),
                        testSession.getSlot().getProxy().getId());
                getRegistry().terminate(testSession, SessionTerminationReason.CLIENT_STOPPED_SESSION);
            }
            return successResponseAction;
        } else {
            return new ResponseAction(ERROR_ACTION_NOT_IMPLEMENTED_GIVEN_ACTION + action, RESPONSE_STATUS_400);
        }
    }
}

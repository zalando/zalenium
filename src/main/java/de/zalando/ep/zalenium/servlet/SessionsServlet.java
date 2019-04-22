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

    public SessionsServlet(ZaleniumRegistry registry) {
        super(registry);
    }

    @Override
    protected ResponseAction doAction(final String action) {
        if (DO_CLEANUP_ACTIVE_SESSIONS.equals(action)) {
            for (TestSession testSession : getRegistry().getActiveSessions()) {
                LOGGER.debug("Delete session {}", testSession.getExternalKey());
                getRegistry().terminate(testSession, SessionTerminationReason.CLIENT_STOPPED_SESSION);
                LOGGER.debug("Release slot {}", testSession.getSlot().getInternalKey());
                getRegistry().forceRelease(testSession.getSlot(), SessionTerminationReason.CLIENT_STOPPED_SESSION);
            }
            return successResponseAction;
        } else {
            return new ResponseAction(ERROR_ACTION_NOT_IMPLEMENTED_GIVEN_ACTION + action, RESPONSE_STATUS_400);
        }
    }
}

package de.zalando.ep.zalenium.dashboard;

import de.zalando.ep.zalenium.servlet.ActionsServlet;

@SuppressWarnings("WeakerAccess")
public class DashboardCleanupServlet extends ActionsServlet {

    private static final String DO_RESET = "doReset";
    private static final String DO_CLEANUP = "doCleanup";

    private static final long serialVersionUID = 1L;

    @Override
    protected ResponseAction doAction(final String action) {
        if (DO_RESET.equals(action) || DO_CLEANUP.equals(action)) {
            if (DO_RESET.equals(action)) {
                DashboardCollection.resetDashboard();
            } else if (DO_CLEANUP.equals(action)) {
                DashboardCollection.cleanupDashboard();
            }
            return successResponseAction;
        } else {
            return new ResponseAction(ERROR_ACTION_NOT_IMPLEMENTED_GIVEN_ACTION + action, RESPONSE_STATUS_400);
        }
    }
}

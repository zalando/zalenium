package de.zalando.ep.zalenium.dashboard;

import de.zalando.ep.zalenium.dashboard.remote.RemoteDriverLogDashboard;
import de.zalando.ep.zalenium.dashboard.remote.RemoteSeleniumLogDashboard;
import de.zalando.ep.zalenium.dashboard.remote.RemoteVideoDashboard;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import static java.lang.System.*;


/**
 * Class in charge of knowing which dashboard to maintain
 */
public class DashboardCollection {
    private static final Logger LOGGER = Logger.getLogger(DashboardCollection.class.getName());

    public static List<DashboardInterface> remoteDashboards;
    public static DashboardInterface localDashboard = new Dashboard();
    public static boolean remoteDashboardsEnabled = getenv("REMOTE_DASHBOARD_HOST") != null;

    static {
        remoteDashboards = new ArrayList<DashboardInterface>() {{
            add(new RemoteVideoDashboard() {{ setUrl(getenv("REMOTE_DASHBOARD_HOST"));}});
            add(new RemoteDriverLogDashboard() {{ setUrl(getenv("REMOTE_DASHBOARD_HOST"));}});
            add(new RemoteSeleniumLogDashboard() {{ setUrl(getenv("REMOTE_DASHBOARD_HOST"));}});
        }};
    }

    public static synchronized void updateDashboard(TestInformation testInformation) {
        String errMsg = "Error during update of dashboard: ";
        if (false == remoteDashboardsEnabled) {
            try {
                localDashboard.updateDashboard(testInformation);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, errMsg + e.toString());
            }
        } else {
            for (DashboardInterface dashboard : remoteDashboards) {
                try {

                    dashboard.updateDashboard(testInformation);
                } catch (NotImplementedException e) {

                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, errMsg + e.toString());
                }
             }
        }
    }

    public static synchronized void cleanupDashboard() throws IOException {
        String errMsg = "Error during cleanup of dashboard: ";
        if (false == remoteDashboardsEnabled) {
            try {
                localDashboard.cleanupDashboard();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, errMsg + e.toString());
            }
        } else {
            for (DashboardInterface dashboard : remoteDashboards) {
                try {
                    dashboard.cleanupDashboard();
                } catch (NotImplementedException e) {
                    //ignore
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, errMsg + e.toString());
                }
            }
        }
    }
}

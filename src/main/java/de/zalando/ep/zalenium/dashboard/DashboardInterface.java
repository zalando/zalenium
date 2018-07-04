package de.zalando.ep.zalenium.dashboard;


/**
 * Represents some class that maintains information about executed tests
 */
public interface DashboardInterface {
    void cleanupDashboard() throws Exception;
    void updateDashboard(TestInformation testInformation) throws Exception;
}
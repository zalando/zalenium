package de.zalando.tip.zalenium.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DashboardTest {

    private TestInformation ti = new TestInformation("seleniumSessionId", "testName", "proxyName", "browser",
            "browserVersion", "platform");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void restoreCommonProxyUtilities() {
        Dashboard.restoreCommonProxyUtilities();
    }

    @Before
    public void initDashboard() throws IOException {
        Dashboard.setExecutedTests(0);
        DashboardTestingSupport.ensureRequiredInputFilesExist(temporaryFolder);
        DashboardTestingSupport.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);

        Dashboard.updateDashboard(ti);
    }

    @Test
    public void testCountOne() throws IOException {
        Assert.assertEquals(1, Dashboard.getExecutedTests());
    }

    @Test
    public void testCountTwo() throws IOException {
        Dashboard.updateDashboard(ti);
        Assert.assertEquals(2, Dashboard.getExecutedTests());
    }

    @Test
    public void missingExecutedTestsFile() throws IOException {
        cleanTempVideosFolder();
        DashboardTestingSupport.ensureRequiredInputFilesExist(temporaryFolder);
        Dashboard.updateDashboard(ti);
        Assert.assertEquals(1, Dashboard.getExecutedTests());
    }

    private void cleanTempVideosFolder() throws IOException {
        FileUtils.cleanDirectory(new File(temporaryFolder.getRoot().getAbsolutePath()));
    }
}

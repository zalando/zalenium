package de.zalando.tip.zalenium.util;

import static java.nio.charset.StandardCharsets.UTF_8;

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

    public static final String TEST_COUNT_FILE_NAME = "amount_of_run_tests.txt";

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

    @Test
    public void nonNumberContentsIgnored() throws IOException {
        File testCountFile = new File(temporaryFolder.getRoot().getAbsolutePath() + "/" + Dashboard.VIDEOS_FOLDER_NAME
                + "/" + TEST_COUNT_FILE_NAME);
        FileUtils.writeStringToFile(testCountFile, "Not-A-Number", UTF_8);
        Dashboard.setExecutedTests(0);
        Dashboard.updateDashboard(ti);
        Assert.assertEquals("1", FileUtils.readFileToString(testCountFile, UTF_8));
    }

    private void cleanTempVideosFolder() throws IOException {
        FileUtils.cleanDirectory(new File(temporaryFolder.getRoot().getAbsolutePath()));
    }
}

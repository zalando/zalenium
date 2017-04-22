package de.zalando.ep.zalenium.util;

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

    @Before
    public void initDashboard() throws IOException {
        Dashboard.setExecutedTests(0, 0);
        TestUtils.ensureRequiredInputFilesExist(temporaryFolder);
        TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @After
    public void restoreCommonProxyUtilities() {
        Dashboard.restoreCommonProxyUtilities();
    }

    @Test
    public void testCountOne() throws IOException {
        Dashboard.updateDashboard(ti);
        Assert.assertEquals(1, Dashboard.getExecutedTests());
        Assert.assertEquals(1, Dashboard.getExecutedTestsWithVideo());
    }

    @Test
    public void testCountTwo() throws IOException {
        Dashboard.updateDashboard(ti);
        Dashboard.updateDashboard(ti);
        Assert.assertEquals(2, Dashboard.getExecutedTests());
        Assert.assertEquals(2, Dashboard.getExecutedTestsWithVideo());
    }

    @Test
    public void missingExecutedTestsFile() throws IOException {
        Dashboard.updateDashboard(ti);
        cleanTempVideosFolder();
        TestUtils.ensureRequiredInputFilesExist(temporaryFolder);
        Dashboard.updateDashboard(ti);
        Assert.assertEquals(1, Dashboard.getExecutedTests());
        Assert.assertEquals(1, Dashboard.getExecutedTestsWithVideo());
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

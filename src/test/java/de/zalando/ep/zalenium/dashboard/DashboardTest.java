package de.zalando.ep.zalenium.dashboard;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;

import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DashboardTest {

    private static final String TEST_COUNT_FILE_NAME = "executedTestsInfo.json";

    private TestInformation ti = new TestInformation.TestInformationBuilder()
            .withSeleniumSessionId("seleniumSessionId")
            .withTestName("testName")
            .withProxyName("proxyName")
            .withBrowser("browser")
            .withBrowserVersion("browserVersion")
            .withPlatform("platform")
            .withTestStatus(TestInformation.TestStatus.COMPLETED)
            .build();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void initDashboard() throws IOException {
        ti.setVideoRecorded(true);
        Dashboard.setExecutedTests(0, 0);
        TestUtils.ensureRequiredInputFilesExist(temporaryFolder);
        CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
        Dashboard.setCommonProxyUtilities(proxyUtilities);
    }

    @After
    public void restoreCommonProxyUtilities() {
        Dashboard.restoreCommonProxyUtilities();
    }

    @Test
    public void testCountOne() {
        Dashboard dashboard = new Dashboard();
        dashboard.updateDashboard(ti);
        Assert.assertEquals(1, Dashboard.getExecutedTests());
        Assert.assertEquals(1, Dashboard.getExecutedTestsWithVideo());
    }

    @Test
    public void testCountTwo() {
        Dashboard dashboard = new Dashboard();
        dashboard.updateDashboard(ti);
        dashboard.updateDashboard(ti);
        Assert.assertEquals(2, Dashboard.getExecutedTests());
        Assert.assertEquals(2, Dashboard.getExecutedTestsWithVideo());
    }

    @Test
    public void missingExecutedTestsFile()  throws IOException {
        Dashboard dashboard = new Dashboard();
        dashboard.updateDashboard(ti);

        cleanTempVideosFolder();
        TestUtils.ensureRequiredInputFilesExist(temporaryFolder);
        dashboard.updateDashboard(ti);
        Assert.assertEquals(1, Dashboard.getExecutedTests());
        Assert.assertEquals(1, Dashboard.getExecutedTestsWithVideo());
    }

    @Test
    public void nonNumberContentsIgnored() throws IOException {
        File testCountFile = new File(temporaryFolder.getRoot().getAbsolutePath() + "/" + Dashboard.VIDEOS_FOLDER_NAME
                + "/" + TEST_COUNT_FILE_NAME);
        JsonObject testQuantities = new JsonObject();
        testQuantities.addProperty("executedTests", "Not-A-Number");
        testQuantities.addProperty("executedTestsWithVideo", "Not-A-Number");
        FileUtils.writeStringToFile(testCountFile, testQuantities.toString(), UTF_8);
        Dashboard.setExecutedTests(0, 0);
        DashboardCollection.updateDashboard(ti);
        Assert.assertEquals(1, Dashboard.getExecutedTests());
        Assert.assertEquals(1, Dashboard.getExecutedTestsWithVideo());
    }

    private void cleanTempVideosFolder() throws IOException {
        FileUtils.cleanDirectory(new File(temporaryFolder.getRoot().getAbsolutePath()));
    }
}

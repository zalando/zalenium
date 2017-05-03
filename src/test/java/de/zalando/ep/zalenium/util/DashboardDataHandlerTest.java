package de.zalando.ep.zalenium.util;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class DashboardDataHandlerTest extends DashboardTempFileBase {

    @Test
    public void addNewTest() throws IOException {
        TestInformation toBeAdded = createTestInformation(1);
        TestInformationRepository tiRepo = DashboardDataHandler.addNewTest(toBeAdded);

        Assert.assertEquals(1, tiRepo.size());
        Assert.assertEquals("testName-1", tiRepo.get(0).getTestName());
        assertFileInTargetExists(tiRepo, DashboardDataHandler.DASHBOARD_DATA_FILENAME);
    }

    @Test
    public void addTwoNewTests() throws IOException {
        TestInformation ti1 = createTestInformation(1);
        DashboardDataHandler.addNewTest(ti1);
        TestInformation ti2 = createTestInformation(2);
        TestInformationRepository tiRepo = DashboardDataHandler.addNewTest(ti2);

        Assert.assertEquals(2, tiRepo.size());
        Assert.assertEquals("testName-2", tiRepo.get(1).getTestName());
    }

    @Test
    public void resourcesWillBeCopied() throws IOException {
        TestInformation ti1 = createTestInformation(1);
        DashboardDataHandler.addNewTest(ti1);
        File zalandoIco = new File(ti1.getVideoFolderPath(), DashboardDataHandler.RESOURCE_ZALANDO_ICO);
        Assert.assertTrue(zalandoIco.exists());
    }

    @Test
    public void clearRecordedVideosAndLogs() throws IOException {
        TestInformation ti1 = createTestInformation(1);
        DashboardDataHandler.addNewTest(ti1);
        File testDirectory = new File(ti1.getVideoFolderPath(), "testdirectory");
        File testFile = new File(ti1.getVideoFolderPath(), "testfile");
        Assert.assertTrue("Expected directory creation to succeed", testDirectory.mkdir());
        Assert.assertTrue("Expected file creation to succeed", testFile.createNewFile());

        DashboardDataHandler.clearRecordedVideosAndLogs(ti1.getVideoFolderPath());
        Assert.assertFalse(testDirectory.exists());
        Assert.assertFalse(testFile.exists());
    }

    @Test(expected = IOException.class)
    public void clearRecordedVideosAndLogsNonDirectoryPathGiven() throws IOException {
        File someFile = new File(temporaryFolder.getRoot().getAbsolutePath(), "file");
        Assert.assertTrue("Expected file creation to succeed", someFile.createNewFile());

        DashboardDataHandler.clearRecordedVideosAndLogs(someFile.getAbsolutePath());
    }

    @Test
    public void clearRecordedVideosAndLogsDirectoryNotExists() throws IOException {
        TestInformation ti1 = createTestInformation(1);
        File notYetExists = new File(ti1.getVideoFolderPath());
        Assert.assertFalse("Expected path to not exist yet", notYetExists.exists());

        DashboardDataHandler.clearRecordedVideosAndLogs(ti1.getVideoFolderPath());
        Assert.assertTrue("Folder should have been created", notYetExists.exists());
    }

    @Test
    public void clearRecordedVideosAndLogsInvalidArgument() throws IOException {
        DashboardDataHandler.clearRecordedVideosAndLogs(null);
        Assert.assertTrue("This test is fine if no excpetion has been thrown", true);
    }

    @Test
    public void clearRecordedVideosAndLogsEmptyArgument() throws IOException {
        DashboardDataHandler.clearRecordedVideosAndLogs("");
        Assert.assertTrue("This test is fine if no excpetion has been thrown", true);
    }
}

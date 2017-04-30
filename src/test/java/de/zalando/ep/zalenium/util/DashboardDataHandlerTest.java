package de.zalando.ep.zalenium.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DashboardDataHandlerTest {
    String tempDashboardPath;
    CommonProxyUtilities mockCommonProxyUtilities;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void initTempPath() {
        tempDashboardPath = temporaryFolder.getRoot().getAbsolutePath();
        mockCommonProxyUtilities = mock(CommonProxyUtilities.class);
        when(mockCommonProxyUtilities.currentLocalPath()).thenReturn(tempDashboardPath);
        when(mockCommonProxyUtilities.getCurrentDateAndTimeFormatted()).thenCallRealMethod();
        TestInformation.setCommonProxyUtilities(mockCommonProxyUtilities);
    }

    @After
    public void resetCommonProxyUtilities() {
        TestInformation.setCommonProxyUtilities(new CommonProxyUtilities());
    }

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

    TestInformation createTestInformation(int testNum) {
        return new TestInformation("seleniumSessionId-" + testNum, "testName-" + testNum, "proxyName", "browser",
                "browserVersion", "platform");
    }

    void assertFileInTargetExists(TestInformationRepository tiRepo, String expectedFile) {
        File toBeTested = new File(tiRepo.getVideoFolderPath(), expectedFile);
        Assert.assertTrue(toBeTested.exists());
    }
}

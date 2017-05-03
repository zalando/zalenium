package de.zalando.ep.zalenium.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class DashboardTempFileBase {
    protected String tempDashboardPath;
    protected CommonProxyUtilities mockCommonProxyUtilities;

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

    protected TestInformation createTestInformation(int testNum) {
        return new TestInformation("seleniumSessionId-" + testNum, "testName-" + testNum, "proxyName", "browser",
                "browserVersion", "platform");
    }

    protected void assertFileInTargetExists(TestInformationRepository tiRepo, String expectedFile) {
        File toBeTested = new File(tiRepo.getVideoFolderPath(), expectedFile);
        Assert.assertTrue(toBeTested.exists());
    }
}

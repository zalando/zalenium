package de.zalando.tip.zalenium.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.rules.TemporaryFolder;

public class DashboardTestingSupport {

    public static void mockCommonProxyUtilitiesForDashboardTesting(TemporaryFolder temporaryFolder) {
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.currentLocalPath()).thenReturn(temporaryFolder.getRoot().getAbsolutePath());
        when(commonProxyUtilities.getShortDateAndTime()).thenCallRealMethod();
        Dashboard.setCommonProxyUtilities(commonProxyUtilities);
    }

    public static void ensureRequiredInputFilesExist(TemporaryFolder temporaryFolder) throws IOException {
        temporaryFolder.newFile("list_template.html");
        temporaryFolder.newFile("dashboard_template.html");
        temporaryFolder.newFile("zalando.ico");
        temporaryFolder.newFolder("css");
        temporaryFolder.newFolder("js");
    }
}

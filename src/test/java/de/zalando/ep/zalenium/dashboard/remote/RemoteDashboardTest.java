package de.zalando.ep.zalenium.dashboard.remote;

import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.TestUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class RemoteDashboardTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private List<RemoteDashboard> dashboardsToTest = new ArrayList<RemoteDashboard>();

    private TestInformation.TestInformationBuilder builder = new TestInformation.TestInformationBuilder()
            .withSeleniumSessionId("seleniumSessionId")
            .withTestName("testName")
            .withFileExtension(".mp4")
            .withProxyName("proxyName")
            .withBrowser("browser")
            .withBrowserVersion("browserVersion")
            .withPlatform("platform")
            .withProxyName("zalenium")
            .withTestStatus(TestInformation.TestStatus.COMPLETED);


    public RemoteDashboardTest() {
        dashboardsToTest.add(new RemoteVideoDashboard());
        dashboardsToTest.add(new RemoteSeleniumLogDashboard());
        dashboardsToTest.add(new RemoteDriverLogDashboard());
    }

    @Test
    public void remoteHostNotSet() throws Exception {
        FormPoster mockFormPoster = mock(FormPoster.class);
        when(mockFormPoster.getRemoteHost()).thenReturn(null);
        when(mockFormPoster.post(any())).thenThrow(new AssertionError("Remote dashboard classes may not Post() if Url is not set."));

        TestInformation ti = this.builder.build();
        for( RemoteDashboard  d: this.dashboardsToTest) {
            d.setFormPoster(mockFormPoster);
            d.updateDashboard(ti);
        }
    }

    @Test
    public void filesDoNotExist() throws Exception {
        FormPoster mockFormPoster = mock(FormPoster.class);
        when(mockFormPoster.post(any())).thenThrow(new AssertionError("Remote dashboard may not Post() when file does not exist"));
        when(mockFormPoster.getRemoteHost()).thenReturn("http://localhost");

        CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
        Dashboard.setCommonProxyUtilities(proxyUtilities);
        TestInformation ti = builder.build();
        ti.setVideoRecorded(true);

        for( RemoteDashboard  d : this.dashboardsToTest) {
            d.setFormPoster(mockFormPoster);
            try {
                d.updateDashboard(ti);
                Assert.fail("An IOException was expected due to missing file");
            } catch (IOException e) {
                //ignore
            }
        }
    }




}

package de.zalando.tip.zalenium.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Class in charge of building the dashboard, using templates and coordinating video downloads.
 */

public class Dashboard {

    @SuppressWarnings("WeakerAccess")
    public static final String VIDEOS_FOLDER_NAME = "videos";

    private static CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();

    private static int executedTests = 0;

    public static synchronized void updateDashboard(TestInformation testInformation) throws IOException {
        String currentLocalPath = commonProxyUtilities.currentLocalPath();
        String localVideosPath = currentLocalPath + "/" + VIDEOS_FOLDER_NAME;

        String testEntry = FileUtils.readFileToString(new File(currentLocalPath, "list_template.html"), StandardCharsets.UTF_8);
        testEntry = testEntry.replace("{fileName}", testInformation.getFileName()).
                replace("{testName}", testInformation.getTestName()).
                replace("{dateAndTime}", commonProxyUtilities.getShortDateAndTime()).
                replace("{browserAndPlatform}", testInformation.getBrowserAndPlatform()).
                replace("{proxyName}", testInformation.getProxyName());

        File testList = new File(localVideosPath, "list.html");
        // Putting the new entry at the top
        if (testList.exists()) {
            String testListContents = FileUtils.readFileToString(testList, StandardCharsets.UTF_8);
            testEntry = testEntry.concat("\n").concat(testListContents);
        }
        FileUtils.writeStringToFile(testList, testEntry, StandardCharsets.UTF_8);

        executedTests++;

        File dashboardHtml = new File(localVideosPath, "dashboard.html");
        String dashboard = FileUtils.readFileToString(new File(currentLocalPath, "dashboard_template.html"), StandardCharsets.UTF_8);
        dashboard = dashboard.replace("{testList}", testEntry);
        FileUtils.writeStringToFile(dashboardHtml, dashboard, StandardCharsets.UTF_8);

        File testCountFile = new File(localVideosPath, "amount_of_run_tests.txt");
        FileUtils.writeStringToFile(testCountFile, String.valueOf(executedTests), StandardCharsets.UTF_8);

        File zalandoIco = new File(localVideosPath, "zalando.ico");
        if (!zalandoIco.exists()) {
            FileUtils.copyFile(new File(currentLocalPath, "zalando.ico"), zalandoIco);
        }

        File cssFolder = new File(localVideosPath + "/css");
        File jsFolder = new File(localVideosPath + "/js");

        if (!cssFolder.exists()) {
            FileUtils.copyDirectory(new File(currentLocalPath + "/css"), cssFolder);
        }
        if (!jsFolder.exists()) {
            FileUtils.copyDirectory(new File(currentLocalPath + "/js"), jsFolder);
        }
    }

}

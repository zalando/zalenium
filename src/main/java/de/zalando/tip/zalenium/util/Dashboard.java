package de.zalando.tip.zalenium.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Class in charge of building the dashboard, using templates and coordinating video downloads.
 */

public class Dashboard {

    public static final String VIDEOS_FOLDER_NAME = "videos";

    private static CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();

    public static synchronized void updateDashboard(String testName, long duration, String proxyName, String browser,
                                             String platform, String fileName, String path) throws IOException {
        String currentLocalPath = commonProxyUtilities.currentLocalPath();
        // Show duration of 80 seconds like 1m20s
        long minutes = duration / 60;
        long seconds = duration - (minutes * 60);
        String testDuration = String.format("%sm%ss", minutes, seconds);

        String testEntry = FileUtils.readFileToString(new File(currentLocalPath, "list_template.html"), StandardCharsets.UTF_8);
        testEntry = testEntry.replace("{fileName}", fileName).
                replace("{testName}", testName).
                replace("{testDuration}", testDuration).
                replace("{browser}", browser).
                replace("{platform}", platform).
                replace("{proxyName}", proxyName);

        File testList = new File(path, "list.html");
        // Putting the new entry at the top
        if (testList.exists()) {
            String testListContents = FileUtils.readFileToString(testList, StandardCharsets.UTF_8);
            testEntry = testEntry.concat("\n").concat(testListContents);
        }
        FileUtils.writeStringToFile(testList, testEntry, StandardCharsets.UTF_8);

        File dashboardHtml = new File(path, "dashboard.html");
        String dashboard = FileUtils.readFileToString(new File(currentLocalPath, "dashboard_template.html"), StandardCharsets.UTF_8);
        dashboard = dashboard.replace("{testList}", testEntry);
        FileUtils.writeStringToFile(dashboardHtml, dashboard, StandardCharsets.UTF_8);

        File zalandoIco = new File(path, "zalando.ico");
        if (!zalandoIco.exists()) {
            FileUtils.copyFile(new File(currentLocalPath, "zalando.ico"), zalandoIco);
        }

        File cssFolder = new File(path + "/css");
        File jsFolder = new File(path + "/js");

        if (!cssFolder.exists()) {
            FileUtils.copyDirectory(new File(currentLocalPath + "/css"), cssFolder);
        }
        if (!jsFolder.exists()) {
            FileUtils.copyDirectory(new File(currentLocalPath + "/js"), jsFolder);
        }

    }

}

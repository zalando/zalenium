package de.zalando.ep.zalenium.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.*;

/**
 * Class in charge of building the dashboard, using templates and coordinating video downloads.
 */

@SuppressWarnings({"ResultOfMethodCallIgnored", "WeakerAccess"})
public class Dashboard {

    public static final String VIDEOS_FOLDER_NAME = "videos";
    public static final String LOGS_FOLDER_NAME = "logs";
    private static final Logger LOGGER = Logger.getLogger(Dashboard.class.getName());
    private static CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
    private static int executedTests = 0;
    private static int executedTestsWithVideo = 0;

    @VisibleForTesting
    public static int getExecutedTests() {
        return executedTests;
    }

    public static synchronized void updateDashboard(TestInformation testInformation) throws IOException {
        String currentLocalPath = commonProxyUtilities.currentLocalPath();
        String localVideosPath = currentLocalPath + "/" + VIDEOS_FOLDER_NAME;

        String testEntry = FileUtils.readFileToString(new File(currentLocalPath, "list_template.html"), UTF_8);
        testEntry = testEntry.replace("{fileName}", testInformation.getFileName()).
                replace("{testName}", testInformation.getTestName()).
                replace("{dateAndTime}", commonProxyUtilities.getShortDateAndTime()).
                replace("{browserAndPlatform}", testInformation.getBrowserAndPlatform()).
                replace("{proxyName}", testInformation.getProxyName()).
                replace("{seleniumLogFileName}", testInformation.getSeleniumLogFileName()).
                replace("{browserDriverLogFileName}", testInformation.getBrowserDriverLogFileName()).
                replace("{browserConsoleLogFileName}", testInformation.getBrowserConsoleLogFileName());

        File testList = new File(localVideosPath, "list.html");
        // Putting the new entry at the top
        if (testList.exists()) {
            if (isFileOlderThanOneDay(testList.lastModified())) {
                LOGGER.log(Level.FINE, "Deleting file older than one day: " + testList.getAbsolutePath());
                testList.delete();
            } else {
                String testListContents = FileUtils.readFileToString(testList, UTF_8);
                testEntry = testEntry.concat("\n").concat(testListContents);
            }
        }
        FileUtils.writeStringToFile(testList, testEntry, UTF_8);

        executedTests++;
        if (testInformation.isVideoRecorded()) {
            executedTestsWithVideo++;
        }

        JsonObject testQuantities = new JsonObject();
        testQuantities.addProperty("executedTests", executedTests);
        testQuantities.addProperty("executedTestsWithVideo", executedTestsWithVideo);
        File testCountFile = new File(localVideosPath, "executedTestsInfo.json");
        if (testCountFile.exists()) {
            if (isFileOlderThanOneDay(testCountFile.lastModified())) {
                LOGGER.log(Level.FINE, "Deleting file older than one day: " + testCountFile.getAbsolutePath());
                testCountFile.delete();
            } else {
                JsonObject executedTestData = new JsonParser().parse(FileUtils.readFileToString(testCountFile, UTF_8)).getAsJsonObject();
                String executedTestsInFile = executedTestData.get("executedTests").getAsString();
                String executedTestsWithVideoInFile = executedTestData.get("executedTestsWithVideo").getAsString();
                try {
                    executedTests = executedTests == 1 ? Integer.parseInt(executedTestsInFile) + 1 : executedTests;
                    executedTestsWithVideo = executedTestsWithVideo <= 1 ?
                            Integer.parseInt(executedTestsWithVideoInFile) + executedTestsWithVideo : executedTestsWithVideo;
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, e.toString(), e);
                }
            }
        }
        LOGGER.log(Level.FINE, "Test count: " + executedTests);
        FileUtils.writeStringToFile(testCountFile, testQuantities.toString(), UTF_8);

        File dashboardHtml = new File(localVideosPath, "dashboard.html");
        String dashboard = FileUtils.readFileToString(new File(currentLocalPath, "dashboard_template.html"), UTF_8);
        dashboard = dashboard.replace("{testList}", testEntry).
                replace("{executedTests}", String.valueOf(executedTests));
        FileUtils.writeStringToFile(dashboardHtml, dashboard, UTF_8);

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

    @VisibleForTesting
    public static void restoreCommonProxyUtilities() {
        commonProxyUtilities = new CommonProxyUtilities();
    }

    public static void setCommonProxyUtilities(CommonProxyUtilities commonProxyUtilities) {
        Dashboard.commonProxyUtilities = commonProxyUtilities;
    }

    @VisibleForTesting
    public static boolean isFileOlderThanOneDay(long lastModified) {
        long timeSinceLastModification = new Date().getTime() - lastModified;
        return timeSinceLastModification > (24 * 60 * 60 * 1000);
    }

}

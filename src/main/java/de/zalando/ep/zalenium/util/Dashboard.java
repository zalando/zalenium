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
    private static final String TEST_COUNT_FILE = "executedTestsInfo.json";
    private static final String TEST_LIST_FILE = "list.html";
    private static final String DASHBOARD_FILE = "dashboard.html";
    private static final String EXECUTED_TESTS_FIELD = "executedTests";
    private static final String EXECUTED_TESTS_WITH_VIDEO_FIELD = "executedTestsWithVideo";
    private static final String ZALANDO_ICO = "zalando.ico";
    private static final String CSS_FOLDER = "/css";
    private static final String JS_FOLDER = "/js";
    private static final Logger LOGGER = Logger.getLogger(Dashboard.class.getName());
    private static CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
    private static int executedTests = 0;
    private static int executedTestsWithVideo = 0;
    private static String currentLocalPath = null;
    private static String localVideosPath = null;

    public static String getCurrentLocalPath() {
        if (currentLocalPath == null ||currentLocalPath.isEmpty()) {
            currentLocalPath = commonProxyUtilities.currentLocalPath();
        }
        return currentLocalPath;
    }

    public static String getLocalVideosPath() {
        if (localVideosPath == null ||localVideosPath.isEmpty()) {
            localVideosPath = getCurrentLocalPath() + "/" + VIDEOS_FOLDER_NAME;
        }
        return localVideosPath;
    }

    @VisibleForTesting
    public static int getExecutedTests() {
        return executedTests;
    }

    @VisibleForTesting
    public static int getExecutedTestsWithVideo() {
        return executedTestsWithVideo;
    }

    @VisibleForTesting
    public static void setExecutedTests(int executedTests, int executedTestsWithVideo) {
        Dashboard.executedTests = executedTests;
        Dashboard.executedTestsWithVideo = executedTestsWithVideo;
    }

    public static synchronized void updateDashboard(TestInformation testInformation) throws IOException {
        File testCountFile = new File(getLocalVideosPath(), TEST_COUNT_FILE);
        synchronizeExecutedTestsValues(testCountFile);

        String testEntry = FileUtils.readFileToString(new File(getCurrentLocalPath(), "list_template.html"), UTF_8);
        testEntry = testEntry.replace("{fileName}", testInformation.getFileName()).
                replace("{testName}", testInformation.getTestName()).
                replace("{dateAndTime}", commonProxyUtilities.getShortDateAndTime()).
                replace("{browserAndPlatform}", testInformation.getBrowserAndPlatform()).
                replace("{proxyName}", testInformation.getProxyName()).
                replace("{seleniumLogFileName}", testInformation.getSeleniumLogFileName()).
                replace("{browserDriverLogFileName}", testInformation.getBrowserDriverLogFileName());

        File testList = new File(getLocalVideosPath(), TEST_LIST_FILE);
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

        LOGGER.log(Level.FINE, "Test count: " + executedTests);
        LOGGER.log(Level.FINE, "Test count with video: " + executedTestsWithVideo);
        JsonObject testQuantities = new JsonObject();
        testQuantities.addProperty(EXECUTED_TESTS_FIELD, executedTests);
        testQuantities.addProperty(EXECUTED_TESTS_WITH_VIDEO_FIELD, executedTestsWithVideo);
        FileUtils.writeStringToFile(testCountFile, testQuantities.toString(), UTF_8);

        File dashboardHtml = new File(getLocalVideosPath(), DASHBOARD_FILE);
        String dashboard = FileUtils.readFileToString(new File(getCurrentLocalPath(), "dashboard_template.html"), UTF_8);
        dashboard = dashboard.replace("{testList}", testEntry).
                replace("{executedTests}", String.valueOf(executedTests));
        FileUtils.writeStringToFile(dashboardHtml, dashboard, UTF_8);

        File zalandoIco = new File(getLocalVideosPath(), ZALANDO_ICO);
        if (!zalandoIco.exists()) {
            FileUtils.copyFile(new File(getCurrentLocalPath(), ZALANDO_ICO), zalandoIco);
        }

        File cssFolder = new File(getLocalVideosPath() + CSS_FOLDER);
        File jsFolder = new File(getLocalVideosPath() + JS_FOLDER);

        if (!cssFolder.exists()) {
            FileUtils.copyDirectory(new File(getCurrentLocalPath() + CSS_FOLDER), cssFolder);
        }
        if (!jsFolder.exists()) {
            FileUtils.copyDirectory(new File(getCurrentLocalPath() + JS_FOLDER), jsFolder);
        }
    }

    @VisibleForTesting
    public static void synchronizeExecutedTestsValues(File testCountFile) throws IOException {
        if (testCountFile.exists()) {
            if (isFileOlderThanOneDay(testCountFile.lastModified())) {
                LOGGER.log(Level.FINE, "Deleting file older than one day: " + testCountFile.getAbsolutePath());
                testCountFile.delete();
            } else {
                JsonObject executedTestData = new JsonParser()
                        .parse(FileUtils.readFileToString(testCountFile, UTF_8))
                        .getAsJsonObject();
                String executedTestsInFile = executedTestData.get(EXECUTED_TESTS_FIELD).getAsString();
                String executedTestsWithVideoInFile = executedTestData.get(EXECUTED_TESTS_WITH_VIDEO_FIELD).getAsString();
                try {
                    executedTests = Integer.parseInt(executedTestsInFile);
                    executedTestsWithVideo = Integer.parseInt(executedTestsWithVideoInFile);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, e.toString(), e);
                }
            }
        } else {
            executedTests = 0;
            executedTestsWithVideo = 0;
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

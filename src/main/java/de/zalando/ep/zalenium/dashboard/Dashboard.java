package de.zalando.ep.zalenium.dashboard;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;

import org.apache.commons.io.FileUtils;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Class in charge of building the dashboard, using templates and coordinating video downloads.
 */

@SuppressWarnings({"ResultOfMethodCallIgnored", "WeakerAccess"})
public class Dashboard implements DashboardInterface {

    public static final String VIDEOS_FOLDER_NAME = "videos";
    public static final String LOGS_FOLDER_NAME = "logs";
    private static final String TEST_COUNT_FILE = "executedTestsInfo.json";
    private static final String TEST_INFORMATION_FILE = "testInformation.json";
    private static final String TEST_LIST_FILE = "list.html";
    private static final String DASHBOARD_FILE = "dashboard.html";
    private static final String DASHBOARD_TEMPLATE_FILE = "dashboard_template.html";
    private static final String EXECUTED_TESTS_FIELD = "executedTests";
    private static final String EXECUTED_TESTS_WITH_VIDEO_FIELD = "executedTestsWithVideo";
    private static final String ZALANDO_ICO = "zalando.ico";
    private static final String CSS_FOLDER = "/css";
    private static final String JS_FOLDER = "/js";
    private static final String ZALENIUM_RETENTION_PERIOD = "ZALENIUM_RETENTION_PERIOD";
    private static final int DEFAULT_RETENTION_PERIOD = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(Dashboard.class.getName());
    private static List<TestInformation> executedTestsInformation = new ArrayList<>();
    private static CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
    private static final Environment defaultEnvironment = new Environment();
    private static Environment env = defaultEnvironment;
    private static int executedTests = 0;
    private static int executedTestsWithVideo = 0;
    private static int retentionPeriod;
    private static AtomicBoolean shutdownHookAdded = new AtomicBoolean(false);
    
    public Dashboard() {
        retentionPeriod = env.getIntEnvVariable(ZALENIUM_RETENTION_PERIOD,
                DEFAULT_RETENTION_PERIOD);
    }

    public static List<TestInformation> getExecutedTestsInformation() {
        return executedTestsInformation;
    }

    public static String getCurrentLocalPath() {
        return commonProxyUtilities.currentLocalPath();
    }

    public static String getLocalVideosPath() {
        return getCurrentLocalPath() + "/" + VIDEOS_FOLDER_NAME;
    }

    @VisibleForTesting
    public static int getExecutedTests() {
        return executedTests;
    }

    @VisibleForTesting
    public static int getExecutedTestsWithVideo() {
        return executedTestsWithVideo;
    }

    @SuppressWarnings("SameParameterValue")
    @VisibleForTesting
    public static void setExecutedTests(int executedTests, int executedTestsWithVideo) {
        Dashboard.executedTests = executedTests;
        Dashboard.executedTestsWithVideo = executedTestsWithVideo;
    }

    public synchronized void updateDashboard(TestInformation testInformation) {
        File testCountFile = new File(getLocalVideosPath(), TEST_COUNT_FILE);
        testInformation.setRetentionDate(commonProxyUtilities.getDateAndTime(testInformation.getTimestamp(), retentionPeriod));
        
        try {
            synchronizeExecutedTestsValues(testCountFile);

            String testEntry = FileUtils.readFileToString(new File(getCurrentLocalPath(), "list_template.html"), UTF_8);
            testEntry = testEntry.replace("{fileName}", testInformation.getFileName())
                    .replace("{testName}", testInformation.getTestName())
                    .replace("{dateAndTime}", commonProxyUtilities.getShortDateAndTime(testInformation.getTimestamp()))
                    .replace("{browserAndPlatform}", testInformation.getBrowserAndPlatform())
                    .replace("{proxyName}", testInformation.getProxyName())
                    .replace("{seleniumLogFileName}", testInformation.getSeleniumLogFileName())
                    .replace("{browserDriverLogFileName}", testInformation.getBrowserDriverLogFileName())
                    .replace("{testStatus}", testInformation.getTestStatus().getTestStatus())
                    .replace("{testBadge}", testInformation.getTestStatus().getTestBadge())
                    .replace("{screenDimension}", testInformation.getScreenDimension())
                    .replace("{timeZone}", testInformation.getTimeZone())
                    .replace("{testBuild}", testInformation.getBuild())
                    .replace("{retentionDate}", commonProxyUtilities.getShortDateAndTime(testInformation.getRetentionDate()));
            
            File testList = new File(getLocalVideosPath(), TEST_LIST_FILE);
            // Putting the new entry at the top
            String testListContents = testEntry;
            if (testList.exists()) {
                testListContents = FileUtils.readFileToString(testList, UTF_8);
                testListContents = testEntry.concat("\n").concat(testListContents);
            }
            FileUtils.writeStringToFile(testList, testListContents, UTF_8);

            executedTests++;
            if (testInformation.isVideoRecorded()) {
                executedTestsWithVideo++;
            }

            LOGGER.debug("Test count: " + executedTests);
            LOGGER.debug("Test count with video: " + executedTestsWithVideo);
            JsonObject testQuantities = new JsonObject();
            testQuantities.addProperty(EXECUTED_TESTS_FIELD, executedTests);
            testQuantities.addProperty(EXECUTED_TESTS_WITH_VIDEO_FIELD, executedTestsWithVideo);
            FileUtils.writeStringToFile(testCountFile, testQuantities.toString(), UTF_8);

            File dashboardHtml = new File(getLocalVideosPath(), DASHBOARD_FILE);
            String dashboard = FileUtils.readFileToString(new File(getCurrentLocalPath(), DASHBOARD_TEMPLATE_FILE), UTF_8);
            dashboard = dashboard.replace("{testList}", testListContents)
                    .replace("{executedTests}", String.valueOf(executedTests))
                    .replace("{retentionPeriod}", String.valueOf(retentionPeriod));
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
            executedTestsInformation.add(testInformation);
        } catch (IOException e) {
            LOGGER.warn("Error while updating the dashboard.", e);
        }
    }

    public synchronized void cleanupDashboard() throws IOException {        
        Map<Boolean, List<TestInformation>> partitioned = executedTestsInformation.stream()
                .collect(Collectors.partitioningBy(testInformation -> testInformation.getRetentionDate().getTime() > new Date().getTime()));
        
        List<TestInformation> validTestsInformation = partitioned.get(true);
        List<TestInformation> invalidTestsInformation = partitioned.get(false);
        
        if(invalidTestsInformation.size() > 0) {
            LOGGER.info("Cleaning up " + invalidTestsInformation.size() + " test from Dashboard");
            File testCountFile = new File(getLocalVideosPath(), TEST_COUNT_FILE);
            File dashboardHtml = new File(getLocalVideosPath(), DASHBOARD_FILE);
            File testList = new File(getLocalVideosPath(), TEST_LIST_FILE);
            
            for(TestInformation testInformation : invalidTestsInformation) {
                deleteIfExists(new File(getLocalVideosPath() + "/" + testInformation.getFileName()));
                deleteIfExists(new File(testInformation.getLogsFolderPath()));
                
                executedTestsInformation.remove(testInformation);
            }
            
            deleteIfExists(dashboardHtml);
            deleteIfExists(testList);
            deleteIfExists(testCountFile);
            
            String dashboard = FileUtils.readFileToString(new File(getCurrentLocalPath(), DASHBOARD_TEMPLATE_FILE), UTF_8);
            dashboard = dashboard.replace("{testList}", "").
                    replace("{executedTests}", "0");
            FileUtils.writeStringToFile(dashboardHtml, dashboard, UTF_8);
            
            for(TestInformation testInformation : validTestsInformation) {
                updateDashboard(testInformation);
            }
        }
    }
    
    public synchronized void resetDashboard() throws IOException {
        LOGGER.info("Reseting Dashboard");
        File testList = new File(getLocalVideosPath(), TEST_LIST_FILE);
        File testCountFile = new File(getLocalVideosPath(), TEST_COUNT_FILE);
        File dashboardHtml = new File(getLocalVideosPath(), DASHBOARD_FILE);
        File logsFolder = new File(getLocalVideosPath(), LOGS_FOLDER_NAME);
        File videosFolder = new File(getLocalVideosPath());
        String[] extensions = new String[] { "mp4", "mkv" };
        for (File file : FileUtils.listFiles(videosFolder, extensions, true)) {
            deleteIfExists(file);
        }
        deleteIfExists(logsFolder);
        deleteIfExists(testList);
        deleteIfExists(testCountFile);
        deleteIfExists(dashboardHtml);
        String dashboard = FileUtils.readFileToString(new File(getCurrentLocalPath(), DASHBOARD_TEMPLATE_FILE), UTF_8);
        dashboard = dashboard.replace("{testList}", "").
                replace("{executedTests}", "0");
        FileUtils.writeStringToFile(dashboardHtml, dashboard, UTF_8);
        
        executedTestsInformation = new ArrayList<>();
    }

    public static void deleteIfExists(File file) {
        if (file.exists()) {
            try {
                FileUtils.forceDelete(file);
            } catch (IOException e) {
                LOGGER.error("Failed to delete file: [" + file.toString() + "]", e);
            }
        }
    }

    @VisibleForTesting
    public static void synchronizeExecutedTestsValues(File testCountFile) {
        if (testCountFile.exists()) {
            try {
                JsonObject executedTestData = new JsonParser()
                        .parse(FileUtils.readFileToString(testCountFile, UTF_8))
                        .getAsJsonObject();
                String executedTestsInFile = executedTestData.get(EXECUTED_TESTS_FIELD).getAsString();
                String executedTestsWithVideoInFile = executedTestData.get(EXECUTED_TESTS_WITH_VIDEO_FIELD).getAsString();
                executedTests = Integer.parseInt(executedTestsInFile);
                executedTestsWithVideo = Integer.parseInt(executedTestsWithVideoInFile);
            } catch (Exception e) {
                LOGGER.warn(e.toString(), e);
            }
        } else {
            executedTests = 0;
            executedTestsWithVideo = 0;
        }
    }

    @VisibleForTesting
    public static void loadTestInformationFromFile() {
        try {
            if (executedTestsInformation.size() == 0) {
                File testInformationFile = new File(getLocalVideosPath(), TEST_INFORMATION_FILE);
                if (testInformationFile.exists()) {
                    String testInformationContents = FileUtils.readFileToString(testInformationFile, UTF_8);
                    Type collectionType = new TypeToken<ArrayList<TestInformation>>(){}.getType();
                    executedTestsInformation = new Gson().fromJson(testInformationContents, collectionType);
                }
            }
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
        }
    }

    @VisibleForTesting
    public static void dumpTestInformationToFile() {
        try {
            if (executedTestsInformation.size() > 0) {
                File testInformationFile = new File(getLocalVideosPath(), TEST_INFORMATION_FILE);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                FileUtils.writeStringToFile(testInformationFile, gson.toJson(executedTestsInformation), UTF_8);
            }
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
        }
    }

    public static void setShutDownHook() {
        if (!shutdownHookAdded.getAndSet(true)) {
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(Dashboard::dumpTestInformationToFile, "Dashboard dumpTestInformationToFile shutdown hook"));
            } catch (Exception e) {
                LOGGER.warn(e.toString(), e);
            }
        }
    }

    @VisibleForTesting
    public static void restoreCommonProxyUtilities() {
        commonProxyUtilities = new CommonProxyUtilities();
    }

    @VisibleForTesting
    public static void setCommonProxyUtilities(CommonProxyUtilities commonProxyUtilities) {
        Dashboard.commonProxyUtilities = commonProxyUtilities;
    }

}
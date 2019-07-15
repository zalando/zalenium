package de.zalando.ep.zalenium.dashboard;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Class in charge of building the dashboard, using templates and coordinating video downloads.
 */

@SuppressWarnings({"WeakerAccess"})
public class Dashboard implements DashboardInterface {

    public static final String VIDEOS_FOLDER_NAME = "videos";
    public static final String LOGS_FOLDER_NAME = "logs";
    private static final String TEST_COUNT_FILE = "executedTestsInfo.json";
    private static final String TEST_INFORMATION_FILE = "testInformation.json";
    private static final String DASHBOARD_FILE = "dashboard.html";
    private static final String DASHBOARD_TEMPLATE_FILE = "dashboard_template.html";
    private static final String EXECUTED_TESTS_FIELD = "executedTests";
    private static final String EXECUTED_TESTS_WITH_VIDEO_FIELD = "executedTestsWithVideo";
    private static final String ZALANDO_ICO = "zalando.ico";
    private static final String CSS_FOLDER = "/css";
    private static final String JS_FOLDER = "/js";
    private static final String IMG_FOLDER = "/img";
    private static final String ZALENIUM_RETENTION_PERIOD = "ZALENIUM_RETENTION_PERIOD";
    private static final int DEFAULT_RETENTION_PERIOD = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(Dashboard.class.getName());
    private static CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
    private static final Environment defaultEnvironment = new Environment();
    private static Environment env = defaultEnvironment;
    private static int executedTests = 0;
    private static int executedTestsWithVideo = 0;
    private static int retentionPeriod = 3;

    public Dashboard() {
        retentionPeriod = env.getIntEnvVariable(ZALENIUM_RETENTION_PERIOD, DEFAULT_RETENTION_PERIOD);
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
            testInformation.buildSeleniumLogFileName();
            testInformation.buildBrowserDriverLogFileName();
            testInformation.setAddedToDashboardTime(new Date().getTime());

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
            CommonProxyUtilities.setFilePermissions(testCountFile.toPath());

            File dashboardHtml = new File(getLocalVideosPath(), DASHBOARD_FILE);
            setupDashboardFile(dashboardHtml);

            File zalandoIco = new File(getLocalVideosPath(), ZALANDO_ICO);
            if (!zalandoIco.exists()) {
                FileUtils.copyFile(new File(getCurrentLocalPath(), ZALANDO_ICO), zalandoIco);
                CommonProxyUtilities.setFilePermissions(zalandoIco.toPath());
            }

            File cssFolder = new File(getLocalVideosPath() + CSS_FOLDER);
            File jsFolder = new File(getLocalVideosPath() + JS_FOLDER);
            File imgFolder = new File(getLocalVideosPath() + IMG_FOLDER);

            if (!cssFolder.exists()) {
                FileUtils.copyDirectory(new File(getCurrentLocalPath() + CSS_FOLDER), cssFolder);
                CommonProxyUtilities.setFilePermissions(cssFolder.toPath());
            }
            if (!jsFolder.exists()) {
                FileUtils.copyDirectory(new File(getCurrentLocalPath() + JS_FOLDER), jsFolder);
                CommonProxyUtilities.setFilePermissions(jsFolder.toPath());
            }
            if (!imgFolder.exists()) {
                FileUtils.copyDirectory(new File(getCurrentLocalPath() + IMG_FOLDER), imgFolder);
                CommonProxyUtilities.setFilePermissions(imgFolder.toPath());
            }
            saveTestInformation(testInformation);
        } catch (IOException e) {
            LOGGER.warn("Error while updating the dashboard.", e);
        }
    }

    public synchronized void cleanupDashboard() throws IOException {
        List<TestInformation> informationList = loadTestInformationFromFile();
        Map<Boolean, List<TestInformation>> partitioned = informationList.stream()
                .collect(Collectors.partitioningBy(testInformation -> testInformation.getRetentionDate().getTime() > new Date().getTime()));

        List<TestInformation> validTestsInformation = partitioned.get(true);
        List<TestInformation> invalidTestsInformation = partitioned.get(false);

        if(invalidTestsInformation.size() > 0) {
            LOGGER.info("Cleaning up " + invalidTestsInformation.size() + " test(s) from Dashboard");

            for(TestInformation testInformation : invalidTestsInformation) {
                deleteIfExists(new File(getLocalVideosPath() + "/" + testInformation.getFileName()));
                deleteIfExists(new File(testInformation.getLogsFolderPath()));
            }

            cleanupFiles(false);
            dumpTestInformationToFile(validTestsInformation);
        }
    }

    public synchronized void resetDashboard() throws IOException {
        LOGGER.info("Resetting Dashboard");
        cleanupFiles(true);
    }

    private void cleanupFiles(boolean reset) throws IOException {
        File testCountFile = new File(getLocalVideosPath(), TEST_COUNT_FILE);
        File dashboardHtml = new File(getLocalVideosPath(), DASHBOARD_FILE);

        deleteIfExists(dashboardHtml);
        deleteIfExists(testCountFile);

        if (reset) {
            File videosFolder = new File(getLocalVideosPath());
            FileUtils.cleanDirectory(videosFolder);
        }

        setupDashboardFile(dashboardHtml);
    }

    private void setupDashboardFile(File dashboardHtml) throws IOException {
        String dashboard = FileUtils.readFileToString(new File(getCurrentLocalPath(), DASHBOARD_TEMPLATE_FILE), UTF_8);
        dashboard = dashboard.replace("{retentionPeriod}", String.valueOf(retentionPeriod));
        FileUtils.writeStringToFile(dashboardHtml, dashboard, UTF_8);
        CommonProxyUtilities.setFilePermissions(dashboardHtml.toPath());
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
    public static List<TestInformation> loadTestInformationFromFile() {
        try {
            List<TestInformation> testInformation = new ArrayList<>();
            File testInformationFile = new File(getLocalVideosPath(), TEST_INFORMATION_FILE);
            if (testInformationFile.exists()) {
                List<String> lines = FileUtils.readLines(testInformationFile, UTF_8);
                Gson gson = new Gson();
                for (String line : lines) {
                    testInformation.add(gson.fromJson(line, TestInformation.class));
                }
            }
            return testInformation;
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
        }
        return new ArrayList<>();
    }

    public static void dumpTestInformationToFile(List<TestInformation> testInformationList) {
        try {
            File testInformationFile = new File(getLocalVideosPath(), TEST_INFORMATION_FILE);
            // Emptying the file first and then replacing it with what comes from testInformationList
            FileUtils.writeStringToFile(testInformationFile, "", UTF_8);
            Gson gson = new GsonBuilder().create();
            for (TestInformation information : testInformationList) {
                FileUtils.writeStringToFile(testInformationFile, gson.toJson(information) + System.lineSeparator(),
                    UTF_8, true);
            }
            CommonProxyUtilities.setFilePermissions(testInformationFile.toPath());
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
        }
    }

    public static void saveDashboard() {
        LOGGER.info("Saving dashboard...");
        List<TestInformation> executedTestsInformation = loadTestInformationFromFile();
        executedTestsInformation.sort(Comparator.comparing(TestInformation::getAddedToDashboardTime));
        String itemTemplate = loadTestItemTemplate();
        List<String> testItems = new ArrayList<>();
        for (TestInformation testInformation : executedTestsInformation) {
            String platformLogo;
            if (testInformation.getPlatform().toLowerCase().contains("mac")) {
                platformLogo = "apple";
            } else if (testInformation.getPlatform().toLowerCase().contains("windows")) {
                platformLogo = "windows";
            } else {
                platformLogo = testInformation.getPlatform().toLowerCase();
            }
            String  buildDirectory = testInformation.getVideoFolderPath().replace("/home/seluser/videos", "");
            buildDirectory = buildDirectory.trim().length() > 0 ? buildDirectory.replace("/", "").concat("/") : "";
            String fileName = buildDirectory.concat(testInformation.getFileName());
            String seleniumLogFileName = "logs/".concat(buildDirectory).concat(testInformation.getSeleniumLogFileName()
                    .replace("logs/", ""));
            String browserDriverLogFileName = "logs/".concat(buildDirectory).concat(testInformation.getBrowserDriverLogFileName()
                    .replace("logs/", ""));
            String testItem = itemTemplate
                    .replace("{fileName}", fileName)
                    .replace("{testName}", testInformation.getTestName())
                    .replace("{seleniumSessionId}", testInformation.getSeleniumSessionId())
                    .replace("{testStatus}", testInformation.getTestStatus().name())
                    .replace("{testStatusLowerCase}", testInformation.getTestStatus().name().toLowerCase())
                    .replace("{browser}", testInformation.getBrowser())
                    .replace("{browserLowerCase}", testInformation.getBrowser().toLowerCase())
                    .replace("{browserVersion}", testInformation.getBrowserVersion())
                    .replace("{platformLogo}", platformLogo)
                    .replace("{proxyName}", testInformation.getProxyName())
                    .replace("{proxyNameLowerCase}", testInformation.getProxyName().toLowerCase())
                    .replace("{timestamp}", testInformation.getTimestamp().toString())
                    .replace("{addedToDashboardTime}", String.valueOf(testInformation.getAddedToDashboardTime()))
                    .replace("{screenDimension}", testInformation.getScreenDimension())
                    .replace("{timeZone}", testInformation.getTimeZone())
                    .replace("{build}", testInformation.getBuild())
                    .replace("{seleniumLogFileName}", seleniumLogFileName)
                    .replace("{browserDriverLogFileName}", browserDriverLogFileName)
                    .replace("{retentionDate}", testInformation.getRetentionDate().toString());
            testItems.add(testItem);
        }

        try {
            File dashboardHtml = new File(getLocalVideosPath(), DASHBOARD_FILE);
            String dashboard = FileUtils.readFileToString(new File(getCurrentLocalPath(), DASHBOARD_TEMPLATE_FILE), UTF_8);
            dashboard = dashboard.replace("list-group\">", String.format("%s%s", "list-group\">",
                    String.join(System.lineSeparator(), Lists.reverse(testItems))))
                    .replace("{retentionPeriod}", String.valueOf(retentionPeriod));
            FileUtils.writeStringToFile(dashboardHtml, dashboard, UTF_8);
            CommonProxyUtilities.setFilePermissions(dashboardHtml.toPath());
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
        }
    }

    private static String loadTestItemTemplate() {
        String templateFile = "html_templates/test_item.html";
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(templateFile);
        try {
            return IOUtils.toString(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn(e.toString(), e);
        }
        return "";
    }


    private void saveTestInformation(TestInformation testInformation) {
        try {
            File testInformationFile = new File(getLocalVideosPath(), TEST_INFORMATION_FILE);
            Gson gson = new GsonBuilder().create();
            FileUtils.writeStringToFile(testInformationFile, gson.toJson(testInformation) + System.lineSeparator(),
                UTF_8, true);
            CommonProxyUtilities.setFilePermissions(testInformationFile.toPath());
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
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

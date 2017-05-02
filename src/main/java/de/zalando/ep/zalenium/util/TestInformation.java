package de.zalando.ep.zalenium.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The purpose of this class is to gather the test information that can be used to render the dashboard.
 */
@SuppressWarnings("WeakerAccess")
public class TestInformation {
    public static final String VIDEOS_FOLDER_NAME = "videos";
    public static final String LOGS_FOLDER_NAME = "logs";
    private static final String TEST_FILE_NAME_TEMPLATE = "{proxyName}_{testName}_{browser}_{platform}_{timestamp}";
    private static final String FILE_NAME_TEMPLATE = "{fileName}{fileExtension}";
    private static CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
    private String seleniumSessionId;
    private String testName;
    private String proxyName;
    private String browser;
    private String browserVersion;
    private String platform;
    private String platformVersion;
    private String fileName;
    private String fileExtension;
    private String videoUrl;
    private List<String> logUrls;
    private String videoFolderPath;
    private String logsFolderPath;
    private String testNameNoExtension;
    private boolean videoRecorded;
    private long recordingTimeMillis;
    private String displayDateAndTime;
    private String browserAndPlatform;

    public TestInformation(String seleniumSessionId, String testName, String proxyName, String browser,
                           String browserVersion, String platform) {
        this(seleniumSessionId, testName, proxyName, browser, browserVersion, platform, "", "", "", new ArrayList<>());
    }

    public TestInformation(String seleniumSessionId, String testName, String proxyName, String browser,
                           String browserVersion, String platform, String platformVersion, String fileExtension,
                           String videoUrl, List<String> logUrls) {
        this.seleniumSessionId = seleniumSessionId;
        this.testName = testName;
        this.proxyName = proxyName;
        this.browser = browser;
        this.browserVersion = browserVersion;
        this.platform = platform;
        this.platformVersion = platformVersion;
        this.videoUrl = videoUrl;
        this.fileExtension = fileExtension;
        this.logUrls = logUrls;
        this.videoRecorded = true;
        setRecordingTimeMillis(System.currentTimeMillis());
        buildVideoFileName();
        this.browserAndPlatform = buildBrowserAndPlatform();
    }

    public boolean isVideoRecorded() {
        return videoRecorded;
    }

    public void setVideoRecorded(boolean videoRecorded) {
        this.videoRecorded = videoRecorded;
    }

    public String getVideoFolderPath() {
        return videoFolderPath;
    }

    public String getTestName() {
        return testName == null ? seleniumSessionId : testName;
    }

    public String getProxyName() {
        return proxyName;
    }

    public String getFileName() {
        return fileName;
    }

    public List<String> getLogUrls() {
        return logUrls;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getLogsFolderPath() {
        return logsFolderPath;
    }

    public String getSeleniumLogFileName() {
        String seleniumLogFileName = LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if ("Zalenium".equalsIgnoreCase(proxyName)) {
            return seleniumLogFileName.concat(String.format("selenium-node-%s-stderr.log", browser.toLowerCase()));
        }
        if ("SauceLabs".equalsIgnoreCase(proxyName)) {
            return seleniumLogFileName.concat("selenium-server.log");
        }
        return seleniumLogFileName.concat("not_implemented.log");
    }

    public String getBrowserDriverLogFileName() {
        String browserDriverLogFileName = LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if ("Zalenium".equalsIgnoreCase(proxyName)) {
            return browserDriverLogFileName.concat(String.format("%s_driver.log", browser.toLowerCase()));
        }
        if ("SauceLabs".equalsIgnoreCase(proxyName)) {
            return browserDriverLogFileName.concat("log.json");
        }
        return browserDriverLogFileName.concat("not_implemented.log");
    }

    public String getBrowserConsoleLogFileName() {
        String browserConsoleLogFileName = LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if ("Zalenium".equalsIgnoreCase(proxyName)) {
            return browserConsoleLogFileName.concat(String.format("%s_browser.log", browser.toLowerCase()));
        }
        return browserConsoleLogFileName.concat("not_implemented.log");
    }

    @SuppressWarnings("SameParameterValue")
    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
        buildVideoFileName();
    }

    public void buildVideoFileName() {
        this.testNameNoExtension = TEST_FILE_NAME_TEMPLATE.replace("{proxyName}", this.proxyName.toLowerCase()).
                replace("{testName}", getTestName()).
                replace("{browser}", this.browser).
                replace("{platform}", this.platform).
                replace("{timestamp}", commonProxyUtilities.getCurrentDateAndTimeFormatted()).
                replace(" ", "_");
        this.fileName = FILE_NAME_TEMPLATE.replace("{fileName}", testNameNoExtension).
                replace("{fileExtension}", fileExtension).
                replace(" ", "_");
        this.videoFolderPath = commonProxyUtilities.currentLocalPath() + "/" + VIDEOS_FOLDER_NAME;
        this.logsFolderPath = commonProxyUtilities.currentLocalPath() + "/" + VIDEOS_FOLDER_NAME + "/" +
                LOGS_FOLDER_NAME + "/" + testNameNoExtension;
    }

    private String buildBrowserAndPlatform() {
        if ("BrowserStack".equalsIgnoreCase(proxyName)) {
            return String.format("%s %s, %s %s", browser, browserVersion, platform, platformVersion);
        }
        return String.format("%s %s, %s", browser, browserVersion, platform);
    }

    public String getBrowserAndPlatform() {
        return browserAndPlatform;
    }

    public String getTestNameNoExtension() {
        return testNameNoExtension;
    }

    public static void setCommonProxyUtilities(CommonProxyUtilities differentCommonProxyUtilities) {
        TestInformation.commonProxyUtilities = differentCommonProxyUtilities;
    }

    public long getRecordingTimeMillis() {
        return recordingTimeMillis;
    }

    public void setRecordingTimeMillis(long recordingTimeMillis) {
        this.recordingTimeMillis = recordingTimeMillis;
        setDisplayDateAndTime(generateDisplayDateAndTime(recordingTimeMillis));
    }

    public static String generateDisplayDateAndTime(long timeMillis) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM HH:mm:ss");
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault());
        return localDateTime.format(formatter);
    }

    public String getDisplayDateAndTime() {
        return displayDateAndTime;
    }

    public void setDisplayDateAndTime(String displayDateAndTime) {
        this.displayDateAndTime = displayDateAndTime;
    }
}

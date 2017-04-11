package de.zalando.tip.zalenium.util;

import java.util.List;

/**
 * The purpose of this class is to gather the test information that can be used to render the dashboard.
 */
@SuppressWarnings("WeakerAccess")
public class TestInformation {
    private static final String TEST_FILE_NAME_TEMPLATE = "{proxyName}_{testName}_{browser}_{platform}_{timestamp}";
    private static final String FILE_NAME_TEMPLATE = "{fileName}{fileExtension}";
    private static final CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
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
    private String videoFolderPath;
    private String logsFolderPath;
    private String testNameNoExtension;

    public TestInformation(String seleniumSessionId, String testName, String proxyName, String browser,
                           String browserVersion, String platform, String platformVersion, String fileExtension,
                           String videoUrl) {
        this.seleniumSessionId = seleniumSessionId;
        this.testName = testName;
        this.proxyName = proxyName;
        this.browser = browser;
        this.browserVersion = browserVersion;
        this.platform = platform;
        this.platformVersion = platformVersion;
        this.videoUrl = videoUrl;
        this.fileExtension = fileExtension;
        buildVideoFileName();
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

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getLogsFolderPath() {
        return logsFolderPath;
    }

    public String getSeleniumLogFileName() {
        String seleniumLogFileName = Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if ("chrome".equalsIgnoreCase(browser)) {
            return seleniumLogFileName.concat("selenium-node-chrome-stderr.log");
        }
        return seleniumLogFileName.concat("selenium-node-firefox-stderr.log");
    }

    public String getBrowserDriverLogFileName() {
        String browserDriverLogFileName = Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if ("chrome".equalsIgnoreCase(browser)) {
            return browserDriverLogFileName.concat("chrome_driver.log");
        }
        return browserDriverLogFileName.concat("firefox_driver.log");
    }

    public String getBrowserConsoleLogFileName() {
        String browserConsoleLogFileName = Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension + "/";
        if ("chrome".equalsIgnoreCase(browser)) {
            return browserConsoleLogFileName.concat("chrome_browser.log");
        }
        return browserConsoleLogFileName.concat("firefox_browser.log");
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
        this.videoFolderPath = commonProxyUtilities.currentLocalPath() + "/" + Dashboard.VIDEOS_FOLDER_NAME;
        this.logsFolderPath = commonProxyUtilities.currentLocalPath() + "/" + Dashboard.VIDEOS_FOLDER_NAME + "/" +
                Dashboard.LOGS_FOLDER_NAME + "/" + testNameNoExtension;
    }

    public String getBrowserAndPlatform() {
        if ("BrowserStack".equalsIgnoreCase(proxyName)) {
            return String.format("%s %s, %s %s", browser, browserVersion, platform, platformVersion);
        }
        return String.format("%s %s, %s", browser, browserVersion, platform);
    }
}

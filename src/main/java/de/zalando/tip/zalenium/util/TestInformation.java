package de.zalando.tip.zalenium.util;

/**
  The purpose of this class is to gather the test information that can be used to render the dashboard.
 */
@SuppressWarnings("WeakerAccess")
public class TestInformation {
    private static final String FILE_NAME_TEMPLATE = "{proxyName}_{testName}_{browser}_{platform}_{timestamp}{fileExtension}";
    private static final CommonProxyUtilities commonProxyUtilities = new CommonProxyUtilities();
    private String seleniumSessionId;
    private String testName;
    private String proxyName;
    private String browser;
    private String browserVersion;
    private String platform;
    private String platformVersion;
    private String fileName;
    private String videoUrl;
    private String videoFolderPath;

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

    public String getBrowserAndPlatform() {
        if ("BrowserStack".equalsIgnoreCase(proxyName)) {
            return String.format("%s %s, %s %s", browser, browserVersion, platform, platformVersion);
        }
        return String.format("%s %s, %s", browser, browserVersion, platform);
    }

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
        this.videoFolderPath = commonProxyUtilities.currentLocalPath() + "/" + Dashboard.VIDEOS_FOLDER_NAME;
        this.fileName = FILE_NAME_TEMPLATE.replace("{proxyName}", this.proxyName.toLowerCase()).
                replace("{testName}", getTestName()).
                replace("{browser}", this.browser).
                replace("{platform}", this.platform).
                replace("{timestamp}", commonProxyUtilities.getCurrentDateAndTimeFormatted()).
                replace("{fileExtension}", fileExtension).
                replace(" ", "_");
    }
}

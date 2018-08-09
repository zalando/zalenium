package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.selenium.remote.server.jmx.ManagedService;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
    This class should work in a similar way as its sibling, SauceLabsRemoteProxy
 */
@ManagedService(description = "BrowserStack TestSlots")
public class BrowserStackRemoteProxy extends CloudTestingRemoteProxy {

    private static final String BROWSER_STACK_URL = getEnv().getStringEnvVariable("BROWSER_STACK_URL", "http://hub-cloud.browserstack.com:80");
    private static final String BROWSER_STACK_ACCOUNT_INFO = "https://www.browserstack.com/automate/plan.json";
    private static final Logger logger = LoggerFactory.getLogger(BrowserStackRemoteProxy.class.getName());
    private static final String BROWSER_STACK_USER = getEnv().getStringEnvVariable("BROWSER_STACK_USER", "");
    private static final String BROWSER_STACK_KEY = getEnv().getStringEnvVariable("BROWSER_STACK_KEY", "");
    private static final String BROWSER_STACK_PROXY_NAME = "BrowserStack";

    public BrowserStackRemoteProxy(RegistrationRequest request, GridRegistry registry) {
        super(updateBSCapabilities(request, BROWSER_STACK_ACCOUNT_INFO), registry);
    }

    @VisibleForTesting
    private static RegistrationRequest updateBSCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement bsAccountInfo = getCommonProxyUtilities().readJSONFromUrl(url, BROWSER_STACK_USER, BROWSER_STACK_KEY);
        try {
            registrationRequest.getConfiguration().capabilities.clear();
            String logMessage = String.format("[BS] Getting account max. concurrency from %s", url);
            int browserStackAccountConcurrency;
            if (bsAccountInfo == null) {
                logMessage = String.format("[BS] Account max. concurrency was NOT fetched from %s", url);
                browserStackAccountConcurrency = 1;
            } else {
                browserStackAccountConcurrency = bsAccountInfo.getAsJsonObject().get("parallel_sessions_max_allowed").getAsInt();
            }
            logger.info(logMessage);
            return addCapabilitiesToRegistrationRequest(registrationRequest, browserStackAccountConcurrency,
                    BROWSER_STACK_PROXY_NAME);
        } catch (Exception e) {
            logger.error(e.toString(), e);
            getGa().trackException(e);
        }
        return addCapabilitiesToRegistrationRequest(registrationRequest, 1, BROWSER_STACK_PROXY_NAME);
    }

    @Override
    public String getUserNameProperty() {
        return "browserstack.user";
    }

    @Override
    public String getUserNameValue() {
        return BROWSER_STACK_USER;
    }

    @Override
    public String getAccessKeyProperty() {
        return "browserstack.key";
    }

    @Override
    public String getAccessKeyValue() {
        return BROWSER_STACK_KEY;
    }

    @Override
    public String getCloudTestingServiceUrl() {
        return BROWSER_STACK_URL;
    }

    @Override
    public TestInformation getTestInformation(String seleniumSessionId) {
        // https://BS_USER:BS_KEY@www.browserstack.com/automate/sessions/SELENIUM_SESSION_ID.json
        String browserStackBaseTestUrl = "https://www.browserstack.com/automate/sessions/";
        String browserStackTestUrl = browserStackBaseTestUrl + String.format("%s.json", seleniumSessionId);
        for (int i = 0; i < 5; i++) {
            try {
                JsonObject testData = getCommonProxyUtilities().readJSONFromUrl(browserStackTestUrl, BROWSER_STACK_USER,
                    BROWSER_STACK_KEY).getAsJsonObject();
                JsonObject automation_session = testData.getAsJsonObject("automation_session");
                String testName = automation_session.get("name").isJsonNull() ? null : automation_session.get("name").getAsString();
                String browser = "N/A";
                if (automation_session.get("browser").isJsonNull()) {
                    if (!automation_session.get("device").isJsonNull()) {
                        browser = automation_session.get("device").getAsString();
                    }
                } else {
                    browser = automation_session.get("browser").getAsString();
                }
                String browserVersion = automation_session.get("browser_version").isJsonNull()
                    ? "N/A" : automation_session.get("browser_version").getAsString();
                String platform = automation_session.get("os").getAsString();
                String platformVersion = automation_session.get("os_version").getAsString();
                String videoUrl = automation_session.get("video_url").getAsString();
                List<String> logUrls = new ArrayList<>();
                if (videoUrl.startsWith("http")) {
                    return new TestInformation.TestInformationBuilder()
                        .withSeleniumSessionId(seleniumSessionId)
                        .withTestName(testName)
                        .withProxyName(getProxyName())
                        .withBrowser(browser)
                        .withBrowserVersion(browserVersion)
                        .withPlatform(platform)
                        .withTestStatus(TestInformation.TestStatus.COMPLETED)
                        .withPlatformVersion(platformVersion)
                        .withFileExtension(getVideoFileExtension())
                        .withVideoUrl(videoUrl)
                        .withLogUrls(logUrls)
                        .withMetadata(getMetadata())
                        .build();
                }
            } catch (Exception e) {
                logger.debug(e.toString(), e);
                try {
                    Thread.sleep(1000 * 10);
                } catch (InterruptedException ie) {
                    logger.debug(ie.toString(), ie);
                }
            }
        }
        return null;
    }

    @Override
    public String getVideoFileExtension() {
        return ".mp4";
    }

    @Override
    public String getProxyName() {
        return BROWSER_STACK_PROXY_NAME;
    }

    @Override
    public String getProxyClassName() {
        return BrowserStackRemoteProxy.class.getName();
    }

}

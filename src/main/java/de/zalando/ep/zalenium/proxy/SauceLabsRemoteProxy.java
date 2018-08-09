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
    Almost all concepts and ideas for this part of the implementation are taken from the open source project seen here:
    https://github.com/rossrowe/sauce-grid-plugin
 */
@ManagedService(description = "SauceLabs TestSlots")
public class SauceLabsRemoteProxy extends CloudTestingRemoteProxy {

    private static final String SAUCE_LABS_ACCOUNT_INFO = "https://saucelabs.com/rest/v1/users/%s";
    private static final String SAUCE_LABS_USER_NAME = getEnv().getStringEnvVariable("SAUCE_USERNAME", "");
    private static final String SAUCE_LABS_ACCESS_KEY = getEnv().getStringEnvVariable("SAUCE_ACCESS_KEY", "");
    private static final String SAUCE_LABS_URL = getEnv().getStringEnvVariable("SAUCE_LABS_URL", "http://ondemand.saucelabs.com:80");
    private static final Logger LOGGER = LoggerFactory.getLogger(SauceLabsRemoteProxy.class.getName());
    private static final String SAUCE_LABS_PROXY_NAME = "SauceLabs";

    public SauceLabsRemoteProxy(RegistrationRequest request, GridRegistry registry) {
        super(updateSLCapabilities(request, String.format(SAUCE_LABS_ACCOUNT_INFO, SAUCE_LABS_USER_NAME)), registry);
    }

    @VisibleForTesting
    static RegistrationRequest updateSLCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement slAccountInfo = getCommonProxyUtilities().readJSONFromUrl(url, SAUCE_LABS_USER_NAME,
                SAUCE_LABS_ACCESS_KEY);
        try {
            registrationRequest.getConfiguration().capabilities.clear();
            String logMessage = String.format("[SL] Account max. concurrency fetched from %s", url);
            int sauceLabsAccountConcurrency;
            if (slAccountInfo == null) {
                logMessage = String.format("[SL] Account max. concurrency was NOT fetched from %s", url);
                sauceLabsAccountConcurrency = 1;
            } else {
                sauceLabsAccountConcurrency = slAccountInfo.getAsJsonObject().getAsJsonObject("concurrency_limit").
                        get("overall").getAsInt();
            }
            LOGGER.info(logMessage);
            return addCapabilitiesToRegistrationRequest(registrationRequest, sauceLabsAccountConcurrency,
                    SAUCE_LABS_PROXY_NAME);
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
            getGa().trackException(e);
        }
        return addCapabilitiesToRegistrationRequest(registrationRequest, 1, SAUCE_LABS_PROXY_NAME);
    }

    @Override
    public String getUserNameProperty() {
        return "username";
    }

    @Override
    public String getUserNameValue() {
        return SAUCE_LABS_USER_NAME;
    }

    @Override
    public String getAccessKeyProperty() {
        return "accessKey";
    }

    @Override
    public String getAccessKeyValue() {
        return SAUCE_LABS_ACCESS_KEY;
    }

    @Override
    public String getCloudTestingServiceUrl() {
        return SAUCE_LABS_URL;
    }

    @Override
    public boolean proxySupportsLatestAsCapability() {
        return true;
    }

    @Override
    public boolean useAuthenticationToDownloadFile() {
        return true;
    }

    @Override
    public TestInformation getTestInformation(String seleniumSessionId) {
        // https://saucelabs.com/rest/v1/SL_USER/jobs/SELENIUM_SESSION_ID
        String sauceLabsTestUrl = "https://saucelabs.com/rest/v1/%s/jobs/%s";
        sauceLabsTestUrl = String.format(sauceLabsTestUrl, SAUCE_LABS_USER_NAME, seleniumSessionId);
        String sauceLabsVideoUrl = sauceLabsTestUrl + "/assets/video.mp4";
        String sauceLabsBrowserLogUrl = sauceLabsTestUrl + "/assets/log.json";
        String sauceLabsSeleniumLogUrl = sauceLabsTestUrl + "/assets/selenium-server.log";
        JsonObject testData = getCommonProxyUtilities().readJSONFromUrl(sauceLabsTestUrl, SAUCE_LABS_USER_NAME,
                SAUCE_LABS_ACCESS_KEY).getAsJsonObject();
        String testName = testData.get("name").isJsonNull() ? null : testData.get("name").getAsString();
        String browser = testData.get("browser").isJsonNull() ? "N/A" : testData.get("browser").getAsString();
        String browserVersion = testData.get("browser_short_version").isJsonNull()
                ? "N/A" : testData.get("browser_short_version").getAsString();
        String platform = testData.get("os").getAsString();
        List<String> logUrls = new ArrayList<>();
        logUrls.add(sauceLabsBrowserLogUrl);
        logUrls.add(sauceLabsSeleniumLogUrl);
        return new TestInformation.TestInformationBuilder()
                .withSeleniumSessionId(seleniumSessionId)
                .withTestName(testName)
                .withProxyName(getProxyName())
                .withBrowser(browser)
                .withBrowserVersion(browserVersion)
                .withPlatform(platform)
                .withTestStatus(TestInformation.TestStatus.COMPLETED)
                .withFileExtension(getVideoFileExtension())
                .withVideoUrl(sauceLabsVideoUrl)
                .withLogUrls(logUrls)
                .withMetadata(getMetadata())
                .build();
    }

    @Override
    public String getVideoFileExtension() {
        return ".mp4";
    }

    @Override
    public String getProxyName() {
        return "SauceLabs";
    }

    @Override
    public String getProxyClassName() {
        return SauceLabsRemoteProxy.class.getName();
    }


}

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
@ManagedService(description = "CrossBrowserTesting TestSlots")
public class CBTRemoteProxy extends CloudTestingRemoteProxy {

    private static final String CBT_ACCOUNT_INFO = "https://app.crossbrowsertesting.com/api/v3/account/maxParallelLimits";
    private static final String CBT_USERNAME = getEnv().getStringEnvVariable("CBT_USERNAME", "");
    private static final String CBT_AUTHKEY = getEnv().getStringEnvVariable("CBT_AUTHKEY", "");
    private static final String CBT_URL = getEnv().getStringEnvVariable("CBT_URL", "http://hub.crossbrowsertesting.com:80");
    private static final Logger LOGGER = LoggerFactory.getLogger(CBTRemoteProxy.class.getName());
    private static final String CBT_PROXY_NAME = "CrossBrowserTesting";

    public CBTRemoteProxy(RegistrationRequest request, GridRegistry registry) {
        super(updateCBTCapabilities(request, CBT_ACCOUNT_INFO), registry);
    }


    @VisibleForTesting
    static RegistrationRequest updateCBTCapabilities(RegistrationRequest registrationRequest, String url) {
        String currentName = Thread.currentThread().getName();
        Thread.currentThread().setName("CrossBrowserTesting");


        JsonElement cbtAccountInfo = getCommonProxyUtilities().readJSONFromUrl(url, CBT_USERNAME,
                CBT_AUTHKEY);
        try {
            registrationRequest.getConfiguration().capabilities.clear();
            String logMessage = String.format("Account max. concurrency fetched from %s", url);
            int cbtAccountConcurrency;
            if (cbtAccountInfo == null) {
                logMessage = String.format("Account max. concurrency was NOT fetched from %s", url);
                cbtAccountConcurrency = 1;
            } else {
                cbtAccountConcurrency = cbtAccountInfo.getAsJsonObject().get("automated").getAsInt();
            }
            LOGGER.info(logMessage);
            Thread.currentThread().setName(currentName);
            return addCapabilitiesToRegistrationRequest(registrationRequest, cbtAccountConcurrency,
                    CBT_PROXY_NAME);
        } catch (Exception e) {
            LOGGER.warn(e.toString(), e);
            getGa().trackException(e);
        }
        Thread.currentThread().setName(currentName);
        return addCapabilitiesToRegistrationRequest(registrationRequest, 1, CBT_PROXY_NAME);
    }


    @Override
    public String getUserNameProperty() {
        return "username";
    }

    @Override
    public String getUserNameValue() {
        return CBT_USERNAME;
    }

    @Override
    public String getAccessKeyProperty() {
        return "password";
    }

    @Override
    public String getAccessKeyValue() {
        return CBT_AUTHKEY;
    }

    @Override
    public String getCloudTestingServiceUrl() {
        return CBT_URL;
    }

    @Override
    public boolean proxySupportsLatestAsCapability() {
        return true;
    }

    @Override
    public TestInformation getTestInformation(String seleniumSessionId) {
        String cbtTestUrl = "https://crossbrowsertesting.com/api/v3/selenium/%s";
        cbtTestUrl = String.format(cbtTestUrl, seleniumSessionId);


        JsonObject testData = getCommonProxyUtilities().readJSONFromUrl(cbtTestUrl, CBT_USERNAME,
                CBT_AUTHKEY).getAsJsonObject();


        String cbtVideoUrl = testData.get("videos").isJsonNull() ?
                null : testData.get("videos").getAsJsonArray().get(0).getAsJsonObject().get("video").getAsString();
        String testName = testData.get("caps").getAsJsonObject().get("name").isJsonNull() ?
                null : testData.get("caps").getAsJsonObject().get("name").getAsString();
        String browser = testData.get("caps").getAsJsonObject().get("browserName").isJsonNull() ?
                null : testData.get("caps").getAsJsonObject().get("browserName").getAsString();
        String browserVersion = testData.get("browser").getAsJsonObject().get("version").isJsonNull() ?
                null : testData.get("browser").getAsJsonObject().get("version").getAsString();
        String platform = testData.get("caps").getAsJsonObject().get("platform").isJsonNull() ?
                null : testData.get("caps").getAsJsonObject().get("platform").getAsString();
        List<String> logUrls = new ArrayList<>();

        return new TestInformation.TestInformationBuilder()
                .withSeleniumSessionId(seleniumSessionId)
                .withTestName(testName)
                .withProxyName(getProxyName())
                .withBrowser(browser)
                .withBrowserVersion(browserVersion)
                .withPlatform(platform)
                .withTestStatus(TestInformation.TestStatus.COMPLETED)
                .withFileExtension(getVideoFileExtension())
                .withVideoUrl(cbtVideoUrl)
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
        return "CrossBrowserTesting";
    }

    @Override
    public String getProxyClassName() {
        return CBTRemoteProxy.class.getName();
    }

}

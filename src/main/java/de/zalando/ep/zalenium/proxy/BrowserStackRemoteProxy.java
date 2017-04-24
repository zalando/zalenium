package de.zalando.ep.zalenium.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.servlet.CloudProxyHtmlRenderer;
import de.zalando.ep.zalenium.util.TestInformation;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    This class should work in a similar way as its sibling, SauceLabsRemoteProxy
 */
public class BrowserStackRemoteProxy extends CloudTestingRemoteProxy {

    private static final String BROWSER_STACK_URL = "http://hub-cloud.browserstack.com:80";
    private static final String BROWSER_STACK_ACCOUNT_INFO = "https://%s:%s@www.browserstack.com/automate/plan.json";
    private static final Logger logger = Logger.getLogger(BrowserStackRemoteProxy.class.getName());
    private static final String BROWSER_STACK_USER = getEnv().getStringEnvVariable("BROWSER_STACK_USER", "");
    private static final String BROWSER_STACK_KEY = getEnv().getStringEnvVariable("BROWSER_STACK_KEY", "");
    private final HtmlRenderer renderer = new CloudProxyHtmlRenderer(this);

    public BrowserStackRemoteProxy(RegistrationRequest request, Registry registry) {
        super(updateBSCapabilities(request, String.format(BROWSER_STACK_ACCOUNT_INFO, BROWSER_STACK_USER,
                BROWSER_STACK_KEY)), registry);
    }

    @VisibleForTesting
    private static RegistrationRequest updateBSCapabilities(RegistrationRequest registrationRequest, String url) {
        JsonElement bsAccountInfo = getCommonProxyUtilities().readJSONFromUrl(url);
        try {
            registrationRequest.getConfiguration().capabilities.clear();
            String userPasswordSuppress = String.format("%s:%s@", BROWSER_STACK_USER, BROWSER_STACK_KEY);
            String logMessage = String.format("[BS] Getting account max. concurrency from %s",
                    url.replace(userPasswordSuppress, ""));
            int browserStackAccountConcurrency;
            if (bsAccountInfo == null) {
                logMessage = String.format("[BS] Account max. concurrency was NOT fetched from %s",
                        url.replace(userPasswordSuppress, ""));
                browserStackAccountConcurrency = 1;
            } else {
                browserStackAccountConcurrency = bsAccountInfo.getAsJsonObject().get("parallel_sessions_max_allowed").getAsInt();
            }
            logger.log(Level.INFO, logMessage);
            return addCapabilitiesToRegistrationRequest(registrationRequest, browserStackAccountConcurrency);
        } catch (Exception e) {
            registrationRequest = addCapabilitiesToRegistrationRequest(registrationRequest, 1);
            logger.log(Level.SEVERE, e.toString(), e);
            getGa().trackException(e);
        }
        return registrationRequest;
    }

    private static RegistrationRequest addCapabilitiesToRegistrationRequest(RegistrationRequest registrationRequest, int concurrency) {
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, concurrency);
        desiredCapabilities.setBrowserName("BrowserStack");
        desiredCapabilities.setPlatform(Platform.ANY);
        registrationRequest.getConfiguration().capabilities.add(desiredCapabilities);
        return registrationRequest;
    }

    @Override
    public HtmlRenderer getHtmlRender() {
        return this.renderer;
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
        String browserStackBaseTestUrl = "https://%s:%s@www.browserstack.com/automate/sessions/";
        browserStackBaseTestUrl = String.format(browserStackBaseTestUrl, getUserNameValue(), getAccessKeyValue());
        String browserStackTestUrl = browserStackBaseTestUrl + String.format("%s.json", seleniumSessionId);
        JsonObject testData = getCommonProxyUtilities().readJSONFromUrl(browserStackTestUrl).getAsJsonObject();
        JsonObject automation_session = testData.getAsJsonObject("automation_session");
        String testName = automation_session.get("name").getAsString();
        String browser = automation_session.get("browser").getAsString();
        String browserVersion = automation_session.get("browser_version").getAsString();
        String platform = automation_session.get("os").getAsString();
        String platformVersion = automation_session.get("os_version").getAsString();
        String videoUrl = automation_session.get("video_url").getAsString();
        List<String> logUrls = new ArrayList<>();
        return new TestInformation(seleniumSessionId, testName, getProxyName(), browser, browserVersion, platform,
                platformVersion, getVideoFileExtension(), videoUrl, logUrls);
    }

    @Override
    public String getVideoFileExtension() {
        return ".mp4";
    }

    @Override
    public String getProxyName() {
        return "BrowserStack";
    }

    @Override
    public String getProxyClassName() {
        return BrowserStackRemoteProxy.class.getName();
    }

}

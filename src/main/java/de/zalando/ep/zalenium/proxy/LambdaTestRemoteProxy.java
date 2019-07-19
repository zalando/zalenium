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
@ManagedService(description = "LambdaTest TestSlots")
public class LambdaTestRemoteProxy extends CloudTestingRemoteProxy {

  private static final String LT_ACCOUNT_INFO = "https://accounts.lambdatest.com/api/user/token/auth";
  private static final String LT_USERNAME = getEnv().getStringEnvVariable("LT_USERNAME", "");
  private static final String LT_ACCESS_KEY = getEnv().getStringEnvVariable("LT_ACCESS_KEY", "");
  private static final String LT_URL = getEnv().getStringEnvVariable("LT_URL", "https://hub.lambdatest.com");
  private static final Logger LOGGER = LoggerFactory.getLogger(LambdaTestRemoteProxy.class.getName());
  private static final String LT_PROXY_NAME = "LambdaTest";

  public LambdaTestRemoteProxy(RegistrationRequest request, GridRegistry registry) {
    super(updateLambdaTestCapabilities(request, LT_ACCOUNT_INFO), registry);
  }

  @VisibleForTesting
  static RegistrationRequest updateLambdaTestCapabilities(RegistrationRequest registrationRequest, String url) {
    String currentName = Thread.currentThread().getName();
    Thread.currentThread().setName("LambdaTest");

    JsonElement lambdaTestAccountInfo = getCommonProxyUtilities().readJSONFromUrl(url, LT_USERNAME, LT_ACCESS_KEY);
    try {
      registrationRequest.getConfiguration().capabilities.clear();
      String logMessage = String.format("Account max. concurrency fetched from %s", url);
      int lambdaTestAccountConcurrency;
      if (lambdaTestAccountInfo == null) {
        logMessage = String.format("Account max. concurrency was NOT fetched from %s", url);
        lambdaTestAccountConcurrency = 1;
      } else {
        lambdaTestAccountConcurrency = lambdaTestAccountInfo.getAsJsonObject().get("organization").getAsJsonObject()
            .get("plan_attributes").getAsJsonObject().get("MAX_PLATFORM_CONCURRENCY").getAsInt();
      }
      LOGGER.info(logMessage);
      Thread.currentThread().setName(currentName);
      return addCapabilitiesToRegistrationRequest(registrationRequest, lambdaTestAccountConcurrency, LT_PROXY_NAME);
    } catch (Exception e) {
      LOGGER.warn(e.toString(), e);
      getGa().trackException(e);
    }
    Thread.currentThread().setName(currentName);
    return addCapabilitiesToRegistrationRequest(registrationRequest, 1, LT_PROXY_NAME);
  }

  @Override
  public String getUserNameProperty() {
    return "user";
  }

  @Override
  public String getUserNameValue() {
    return LT_USERNAME;
  }

  @Override
  public String getAccessKeyProperty() {
    return "accessKey";
  }

  @Override
  public String getAccessKeyValue() {
    return LT_ACCESS_KEY;
  }

  @Override
  public String getCloudTestingServiceUrl() {
    return LT_URL;
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
    String lambdaTestTestUrl = "https://api.lambdatest.com/automation/api/v1/zalenium/%s";
    lambdaTestTestUrl = String.format(lambdaTestTestUrl, seleniumSessionId);

    JsonObject testData = getCommonProxyUtilities().readJSONFromUrl(lambdaTestTestUrl, LT_USERNAME, LT_ACCESS_KEY)
        .getAsJsonObject();
    JsonObject testDataJson = testData.get("data").getAsJsonObject();
    String videoUrl = testDataJson.get("video_url").isJsonNull() ? null : testDataJson.get("video_url").getAsString();
    String testName = testDataJson.get("name").isJsonNull() ? null : testDataJson.get("name").getAsString();
    String browser = testDataJson.get("browser").isJsonNull() ? null : testDataJson.get("browser").getAsString();
    String browserVersion = testDataJson.get("browser_version").isJsonNull() ? null
        : testDataJson.get("browser_version").getAsString();
    String platform = testDataJson.get("platform").isJsonNull() ? null : testDataJson.get("platform").getAsString();

    if (platform != null) {
      platform = platform.toLowerCase().contains("win") ? "windows" : "mac";
    }
    List<String> logUrls = new ArrayList<>();

    List<RemoteLogFile> remoteLogFiles = new ArrayList<>();
    remoteLogFiles.add(new RemoteLogFile((lambdaTestTestUrl + "/selenium/download"), "selenium.log", true));
    remoteLogFiles.add(new RemoteLogFile((lambdaTestTestUrl + "/console/download"), "lambdatest.log", true));

    return new TestInformation.TestInformationBuilder().withSeleniumSessionId(seleniumSessionId).withTestName(testName)
        .withProxyName(getProxyName()).withBrowser(browser).withBrowserVersion(browserVersion).withPlatform(platform)
        .withTestStatus(TestInformation.TestStatus.COMPLETED).withFileExtension(getVideoFileExtension())
        .withVideoUrl(videoUrl).withLogUrls(logUrls).withRemoteLogFiles(remoteLogFiles).withMetadata(getMetadata())
        .build();
  }

  @Override
  public String getVideoFileExtension() {
    return ".mp4";
  }

  @Override
  public String getProxyName() {
    return "LambdaTest";
  }

  @Override
  public String getProxyClassName() {
    return LambdaTestRemoteProxy.class.getName();
  }

}

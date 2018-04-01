package de.zalando.ep.zalenium.util;


import com.google.common.annotations.VisibleForTesting;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small class to create hits in Google Analytics
 * We want to track when a test starts and finishes, also information about errors (Exceptions)
 */
public class GoogleAnalyticsApi {

    private final Logger logger = LoggerFactory.getLogger(GoogleAnalyticsApi.class.getName());

    private HttpClient httpClient;

    private static final String GA_TRACKING_ID = "GA_TRACKING_ID";
    private static final String GA_API_VERSION = "GA_API_VERSION";
    private static final String GA_ANONYMOUS_CLIENT_ID = "GA_ANONYMOUS_CLIENT_ID";

    private final Environment defaultEnvironment = new Environment();
    private Environment env = defaultEnvironment;

    public GoogleAnalyticsApi() {
        this.httpClient = HttpClientBuilder.create().build();
    }

    @VisibleForTesting
    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @VisibleForTesting
    public void setEnv(final Environment env) {
        this.env = env;
    }

    public void testEvent(String nodeHandler, String capabilities, long seconds) {
        String payload = String.format("v=%s&tid=%s&cid=%s&t=event&ec=%s&ea=%s&el=%s&ev=%s", GA_API_VERSION,
                GA_TRACKING_ID, GA_ANONYMOUS_CLIENT_ID, "test", nodeHandler, capabilities, seconds);
        doPost(payload);
    }

    public void trackException(Exception e) {
        String payload = String.format("v=%s&tid=%s&cid=%s&t=exception&exd=%s&exf=%s", GA_API_VERSION, GA_TRACKING_ID,
                GA_ANONYMOUS_CLIENT_ID, e.getMessage(), "0");
        doPost(payload);
    }

    private void doPost(String payload) {
        new Thread(() -> {
            try {
                if (!env.getBooleanEnvVariable("ZALENIUM_SEND_ANONYMOUS_USAGE_INFO", false)) {
                    return;
                }
                String gaApiVersion = env.getStringEnvVariable("ZALENIUM_GA_API_VERSION", "");
                String gaTrackingId = env.getStringEnvVariable("ZALENIUM_GA_TRACKING_ID", "");
                String gaEndpoint = env.getStringEnvVariable("ZALENIUM_GA_ENDPOINT", "");
                String gaAnonymousClientId = env.getStringEnvVariable("ZALENIUM_GA_ANONYMOUS_CLIENT_ID", "");
                String finalPayload = payload.replace(GA_TRACKING_ID, gaTrackingId).replace(GA_API_VERSION, gaApiVersion)
                        .replace(GA_ANONYMOUS_CLIENT_ID, gaAnonymousClientId);
                HttpPost httpPost = new HttpPost(gaEndpoint);
                HttpEntity httpEntity = new ByteArrayEntity(finalPayload.getBytes("UTF-8"));
                httpPost.setEntity(httpEntity);
                httpClient.execute(httpPost);
            } catch (Exception e) {
                logger.debug(e.getMessage(), e);
            }
        }, "GoogleAnalytics doPost").start();
    }



}

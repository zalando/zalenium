package de.zalando.tip.zalenium.proxy;

/*
    Almost all concepts and ideas for common to the implementation that are inherited by the Sauce Labs and
    BrowserStack proxy are taken from the open source project seen here: https://github.com/rossrowe/sauce-grid-plugin
 */

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.tip.zalenium.util.GoogleAnalyticsApi;
import de.zalando.tip.zalenium.util.ZaleniumCapabilityMatcher;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.utils.CapabilityMatcher;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.logging.Logger;

public abstract class CloudTestingRemoteProxy extends DefaultRemoteProxy {

    private static final Logger logger = Logger.getLogger(CloudTestingRemoteProxy.class.getName());
    private static final GoogleAnalyticsApi defaultGA = new GoogleAnalyticsApi();
    private static GoogleAnalyticsApi ga = defaultGA;
    private CapabilityMatcher capabilityHelper;

    CloudTestingRemoteProxy(RegistrationRequest request, Registry registry) {
        super(request, registry);
    }

    @Override
    public void beforeCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof WebDriverRequest && "POST".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (seleniumRequest.getRequestType().equals(RequestType.START_SESSION)) {
                String body = seleniumRequest.getBody();
                JsonObject jsonObject = new JsonParser().parse(body).getAsJsonObject();
                JsonObject desiredCapabilities = jsonObject.getAsJsonObject("desiredCapabilities");
                desiredCapabilities.addProperty(getUserNameProperty(), getUserNameValue());
                desiredCapabilities.addProperty(getAccessKeyProperty(), getAccessKeyValue());
                seleniumRequest.setBody(jsonObject.toString());
            }
        }
        super.beforeCommand(session, request, response);
    }

    @Override
    public void afterCommand(TestSession session, HttpServletRequest request, HttpServletResponse response) {
        if (request instanceof WebDriverRequest && "DELETE".equalsIgnoreCase(request.getMethod())) {
            WebDriverRequest seleniumRequest = (WebDriverRequest) request;
            if (seleniumRequest.getRequestType().equals(RequestType.STOP_SESSION)) {
                long executionTime = (System.currentTimeMillis() - session.getSlot().getLastSessionStart()) / 1000;
                ga.testEvent(BrowserStackRemoteProxy.class.getName(), session.getRequestedCapabilities().toString(),
                        executionTime);
            }
        }
        super.afterCommand(session, request, response);
    }

    String getUserNameProperty() {
        return null;
    }

    String getUserNameValue() {
        return null;
    }

    String getAccessKeyProperty() {
        return null;
    }

    String getAccessKeyValue() {
        return null;
    }

    @Override
    public CapabilityMatcher getCapabilityHelper() {
        if (capabilityHelper == null) {
            capabilityHelper = new ZaleniumCapabilityMatcher(this);
        }
        return capabilityHelper;
    }
    /*
        Making the node seem as heavily used, in order to get it listed after the 'docker-selenium' nodes.
        99% used.
    */
    @Override
    public float getResourceUsageInPercent() {
        return 99;
    }

}

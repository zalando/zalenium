package de.zalando.ep.zalenium.servlet.renderer;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static de.zalando.ep.zalenium.util.ZaleniumConfiguration.ZALENIUM_RUNNING_LOCALLY;

public class LiveNodeHtmlRenderer implements HtmlRenderer {

    private DockerSeleniumRemoteProxy proxy;
    private TemplateRenderer templateRenderer;

    @SuppressWarnings("WeakerAccess")
    public LiveNodeHtmlRenderer(DockerSeleniumRemoteProxy proxy) {
        this.proxy = proxy;
        this.templateRenderer = new TemplateRenderer(getTemplateName());
    }

    /**
     * Platform for docker-selenium will be always Linux.
     */
    @SuppressWarnings("WeakerAccess")
    public static String getPlatform() {
        return Platform.LINUX.toString();
    }

    private String getTemplateName() {
        return "html_templates/live_node_tab.html";
    }

    @Override
    public String renderSummary() {
        StringBuilder testName = new StringBuilder();
        if (!proxy.getTestName().isEmpty()) {
            testName.append("<p>Test name: ").append(proxy.getTestName()).append("</p>");
        }
        StringBuilder testBuild = new StringBuilder();
        if (!proxy.getTestBuild().isEmpty()) {
            testBuild.append("<p>Test build: ").append(proxy.getTestBuild()).append("</p>");
        }

        // Adding live preview
        int noVncPort = proxy.getRegistration().getNoVncPort();
        String noVncIpAddress = proxy.getRegistration().getIpAddress();
        String noVncViewBaseUrl = "/vnc/host/%s/port/%s/?nginx=%s:%s&view_only=%s";
        String noVncReadOnlyUrl = String.format(noVncViewBaseUrl, noVncIpAddress, noVncPort, noVncIpAddress, noVncPort, "true");
        String noVncInteractUrl = String.format(noVncViewBaseUrl, noVncIpAddress, noVncPort, noVncIpAddress, noVncPort, "false");

        if (ZALENIUM_RUNNING_LOCALLY) {
            noVncReadOnlyUrl = String.format("http://localhost:%s/?view_only=false", noVncPort);
            noVncInteractUrl = String.format("http://localhost:%s/?view_only=true", noVncPort);
        }

        Map<String, String> renderSummaryValues = new HashMap<>();
        renderSummaryValues.put("{{proxyName}}", proxy.getClass().getSimpleName());
        renderSummaryValues.put("{{proxyVersion}}", getHtmlNodeVersion());
        renderSummaryValues.put("{{proxyId}}", proxy.getId());
        renderSummaryValues.put("{{containerId}}", proxy.getRegistration().getContainerId());
        renderSummaryValues.put("{{proxyPlatform}}", getPlatform());
        renderSummaryValues.put("{{testName}}", testName.toString());
        renderSummaryValues.put("{{testBuild}}", testBuild.toString());
        renderSummaryValues.put("{{tabBrowsers}}", tabBrowsers());
        renderSummaryValues.put("{{noVncReadOnlyUrl}}", noVncReadOnlyUrl);
        renderSummaryValues.put("{{noVncInteractUrl}}", noVncInteractUrl);
        renderSummaryValues.put("{{tabConfig}}", proxy.getConfig().toString("<p>%1$s: %2$s</p>"));
        return templateRenderer.renderTemplate(renderSummaryValues);
    }

    // content of the browsers tab
    private String tabBrowsers() {
        StringBuilder browserSection = new StringBuilder();
        for (TestSlot testSlot : proxy.getTestSlots()) {
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities(testSlot.getCapabilities());
            String icon = getConsoleIconPath(desiredCapabilities);
            String version = desiredCapabilities.getVersion();
            TestSession session = testSlot.getSession();
            String slotClass = "";
            String slotTitle;
            if (session != null) {
                slotClass = "busy";
                slotTitle = session.get("lastCommand") == null ? "" : session.get("lastCommand").toString();
            } else {
                slotTitle = testSlot.getCapabilities().toString();
            }
            Map<String, String> browserValues = new HashMap<>();
            browserValues.put("{{browserVersion}}", Optional.ofNullable(version).orElse("N/A"));
            browserValues.put("{{slotIcon}}", Optional.ofNullable(icon).orElse("N/A"));
            browserValues.put("{{slotClass}}", slotClass);
            browserValues.put("{{slotTitle}}", slotTitle);
            browserSection.append(templateRenderer.renderSection("{{tabBrowsers}}", browserValues));
        }
        return browserSection.toString();
    }

    private String getConsoleIconPath(DesiredCapabilities cap) {
        String name = cap.getBrowserName().toLowerCase();
        String path = "org/openqa/grid/images/";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path + name + ".png");
        return in == null ? null : "/grid/resources/" + path + name + ".png";
    }

    private String getHtmlNodeVersion() {
        try {
            Map<String, Object> proxyStatus = proxy.getProxyStatus();
            String version = ((Map)(((Map)proxyStatus.get("value"))
                    .get("build")))
                    .get("version").toString();
            return " (version : " + version + ")";
        }catch (Exception e) {
            return " unknown version, " + e.getMessage();
        }
    }
}

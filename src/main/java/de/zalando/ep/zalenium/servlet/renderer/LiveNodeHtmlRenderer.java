package de.zalando.ep.zalenium.servlet.renderer;

import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.web.servlet.beta.MiniCapability;
import org.openqa.grid.web.servlet.beta.SlotsLines;
import org.openqa.selenium.Platform;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LiveNodeHtmlRenderer implements HtmlRenderer {

    private static final Logger LOGGER = Logger.getLogger(LiveNodeHtmlRenderer.class.getName());

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

        Map<String, String> renderSummaryValues = new HashMap<>();
        renderSummaryValues.put("{{proxyName}}", proxy.getClass().getSimpleName());
        renderSummaryValues.put("{{proxyVersion}}", getHtmlNodeVersion());
        renderSummaryValues.put("{{proxyId}}", proxy.getId());
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
        SlotsLines wdLines = new SlotsLines();
        for (TestSlot testSlot : proxy.getTestSlots()) {
            wdLines.add(testSlot);
        }
        StringBuilder browserSection = new StringBuilder();
        for (MiniCapability miniCapability : wdLines.getLinesType()) {
            String icon = miniCapability.getIcon();
            String version = miniCapability.getVersion();
            TestSlot testSlot = wdLines.getLine(miniCapability).get(0);
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

    private String getHtmlNodeVersion() {
        try {
            JsonObject object = proxy.getStatus();
            String version = object.get("value").getAsJsonObject()
                    .get("build").getAsJsonObject()
                    .get("version").getAsString();
            return " (version : " + version + ")";
        } catch (Exception e) {
            LOGGER.log(Level.FINE, e.toString(), e);
            return " unknown version, " + e.getMessage();
        }
    }
}

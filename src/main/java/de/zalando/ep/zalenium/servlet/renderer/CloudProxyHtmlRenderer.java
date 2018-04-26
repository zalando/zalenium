package de.zalando.ep.zalenium.servlet.renderer;

import de.zalando.ep.zalenium.proxy.BrowserStackRemoteProxy;
import de.zalando.ep.zalenium.proxy.SauceLabsRemoteProxy;
import de.zalando.ep.zalenium.proxy.TestingBotRemoteProxy;
import org.openqa.grid.common.exception.GridException;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudProxyHtmlRenderer implements HtmlRenderer {

    private RemoteProxy proxy;
    private TemplateRenderer templateRenderer;

    @SuppressWarnings("unused")
    private CloudProxyHtmlRenderer() {}

    public CloudProxyHtmlRenderer(RemoteProxy proxy) {
        this.proxy = proxy;
        templateRenderer = new TemplateRenderer(getTemplateName());
    }

    private String getTemplateName() {
        return "html_templates/proxy_tab.html";
    }

    public String renderSummary() {
        Map<String, String> renderSummaryValues = new HashMap<>();
        renderSummaryValues.put("{{proxyName}}", proxy.getClass().getSimpleName());
        renderSummaryValues.put("{{proxyVersion}}", getHtmlNodeVersion());
        renderSummaryValues.put("{{proxyId}}", proxy.getId());
        renderSummaryValues.put("{{proxyPlatform}}", getPlatform(proxy));
        renderSummaryValues.put("{{tabBrowsers}}", tabBrowsers());
        renderSummaryValues.put("{{tabConfig}}", tabConfig());
        return templateRenderer.renderTemplate(renderSummaryValues);
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

    // content of the config tab.
    private String tabConfig() {
        return proxy.getConfig().toString("<p>%1$s: %2$s</p>");
    }


    // content of the browsers tab
    private String tabBrowsers() {
        List<TestSlot> testSlots = proxy.getTestSlots();
        StringBuilder slotLines = new StringBuilder();
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities(testSlots.get(0).getCapabilities());
        String version = desiredCapabilities.getVersion();
        if (version != null) {
            version = "v:" + version;
        }
        Map<String, String> linesValues = new HashMap<>();
        linesValues.put("{{browserVersion}}", version);
        // the lines of icons representing the possible slots
        StringBuilder singleSlotsHtml = new StringBuilder();
        for (TestSlot testSlot : testSlots) {
            singleSlotsHtml.append(getSingleSlotHtml(testSlot));
        }
        linesValues.put("{{singleSlots}}", singleSlotsHtml.toString());
        slotLines.append(templateRenderer.renderSection("{{tabBrowsers}}", linesValues));
        return slotLines.toString();
    }

    private String getSingleSlotHtml(TestSlot s) {
        TestSession session = s.getSession();
        String icon = "";
        if (proxy instanceof TestingBotRemoteProxy) {
            icon = "/resources/images/testingbot.png";
        }
        if (proxy instanceof BrowserStackRemoteProxy) {
            icon = "/resources/images/browserstack.png";
        }
        if (proxy instanceof SauceLabsRemoteProxy) {
            icon = "/resources/images/saucelabs.png";
        }
        String slotClass = "";
        String slotTitle;
        if (session != null) {
            slotClass = "busy";
            slotTitle = session.get("lastCommand").toString();
        } else {
            slotTitle = s.getCapabilities().toString();
        }
        Map<String, String> singleSlotValues = new HashMap<>();
        singleSlotValues.put("{{slotIcon}}", icon);
        singleSlotValues.put("{{slotClass}}", slotClass);
        singleSlotValues.put("{{slotTitle}}", slotTitle);
        return templateRenderer.renderSection("{{singleSlots}}", singleSlotValues);
    }

    /**
     * return the platform for the proxy. It should be the same for all slots of the proxy, so checking that.
     * @param proxy remote proxy
     * @return Either the platform name, "Unknown", "mixed OS", or "not specified".
     */
    private static String getPlatform(RemoteProxy proxy) {
        Platform res;
        if (proxy.getTestSlots().size() == 0) {
            return "Unknown";
        }
        res = getPlatform(proxy.getTestSlots().get(0));
        for (TestSlot slot : proxy.getTestSlots()) {
            Platform tmp = getPlatform(slot);
            if (!tmp.is(res)) {
                return "mixed OS";
            }
            res = tmp;
        }
        if (res == null) {
            return "not specified";
        }
        return res.toString();
    }

    private static Platform getPlatform(TestSlot slot) {
        Object o = slot.getCapabilities().get(CapabilityType.PLATFORM_NAME);
        if (o == null) {
            return Platform.ANY;
        }
        if (o instanceof String) {
            return Platform.valueOf((String) o);
        } else if (o instanceof Platform) {
            return (Platform) o;
        } else {
            throw new GridException("Cannot cast " + o + " to org.openqa.selenium.Platform");
        }
    }

}

package de.zalando.tip.zalenium.servlet;

import com.google.gson.JsonObject;
import org.openqa.grid.common.SeleniumProtocol;
import org.openqa.grid.common.exception.GridException;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.web.servlet.beta.MiniCapability;
import org.openqa.grid.web.servlet.beta.SlotsLines;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LiveNodeHtmlRenderer implements HtmlRenderer {

    private static final Logger LOGGER = Logger.getLogger(LiveNodeHtmlRenderer.class.getName());

    private RemoteProxy proxy;

    public LiveNodeHtmlRenderer(RemoteProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public String renderSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div class='proxy'>");
        builder.append("<p class='proxyname'>");
        builder.append(proxy.getClass().getSimpleName());

        builder.append(getHtmlNodeVersion());

        String platform = getPlatform(proxy);

        builder.append("<p class='proxyid'>id : ");
        builder.append(proxy.getId());
        builder.append(", OS : ").append(platform).append("</p>");

        builder.append(nodeTabs());

        builder.append("<div class='content'>");

        builder.append(tabBrowsers());
        builder.append(tabConfig());

        builder.append("</div></div>");

        return builder.toString();
    }

    private String getHtmlNodeVersion() {
        try {
            JsonObject object = proxy.getStatus();
            String version = object.get("value").getAsJsonObject()
                    .get("build").getAsJsonObject()
                    .get("version").getAsString();
            return " (version : "+version+ ")";
        }catch (Exception e) {
            LOGGER.log(Level.FINE, e.toString(), e);
            return " unknown version, "+e.getMessage();
        }
    }

    // content of the config tab.
    private String tabConfig() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div type='config' class='content_detail'>");
        Map<String, Object> config = proxy.getConfig();

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            builder.append("<p>");
            builder.append(entry.getKey());
            builder.append(":");
            builder.append(entry.getValue());
            builder.append("</p>");
        }

        builder.append("</div>");
        return builder.toString();
    }


    // content of the browsers tab
    private String tabBrowsers() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div type='browsers' class='content_detail'>");

        SlotsLines rcLines = new SlotsLines();
        SlotsLines wdLines = new SlotsLines();

        for (TestSlot slot : proxy.getTestSlots()) {
            if (slot.getProtocol() == SeleniumProtocol.Selenium) {
                rcLines.add(slot);
            } else {
                wdLines.add(slot);
            }
        }

        if (rcLines.getLinesType().isEmpty()) {
            builder.append("<p class='protocol' >Remote Control (legacy)</p>");
            builder.append(getLines(rcLines));
        }
        if (wdLines.getLinesType().isEmpty()) {
            builder.append("<p class='protocol' >WebDriver</p>");
            builder.append(getLines(wdLines));
        }

        // Adding live preview
        int vncPort = proxy.getRemoteHost().getPort() + 10000;
        String vncViewBaseUrl = "http://localhost:5555/proxy/%s/?nginx=%s&view_only=%s";
        String vncReadOnlyUrl = String.format(vncViewBaseUrl, vncPort, vncPort, "true");
        String vncInteractUrl = String.format(vncViewBaseUrl, vncPort, vncPort, "false");

        builder.append("<p class='vnc'>");
        builder.append("<a href='").append(vncReadOnlyUrl).append("' target='_blank'>Read-only VNC</a>||");
        builder.append("<a href='").append(vncInteractUrl).append("' target='_blank'>Interact via VNC</a>");
        builder.append("</p>");

        builder.append("<iframe src='").append(vncReadOnlyUrl).append("' class='vnc' ");
        builder.append("style='display: inline-flex; width: 100%; height: 300px; border:none; margin:0; padding:0;'>");
        builder.append("Your browser does not support iframes. </iframe></div>");
        return builder.toString();
    }

    // the lines of icon representing the possible slots
    private String getLines(SlotsLines lines) {
        StringBuilder builder = new StringBuilder();
        for (MiniCapability cap : lines.getLinesType()) {
            String icon = cap.getIcon();
            String version = cap.getVersion();
            builder.append("<p>");
            if (version != null) {
                builder.append("v:").append(version);
            }
            for (TestSlot s : lines.getLine(cap)) {
                builder.append(getSingleSlotHtml(s, icon));
            }
            builder.append("</p>");
        }
        return builder.toString();
    }

    // icon ( or generic html if icon not available )
    private String getSingleSlotHtml(TestSlot s, String icon) {
        StringBuilder builder = new StringBuilder();
        TestSession session = s.getSession();
        if (icon != null) {
            builder.append("<img src='").append(icon).append("' width='16' height='16'");
        } else {
            builder.append("<a href='#' ");
        }

        if (session != null) {
            builder.append(" class='busy' title='").append(session.get("lastCommand")).append("' ");
        } else {
            builder.append(" title='").append(s.getCapabilities()).append("'");
        }

        if (icon != null) {
            builder.append(" />\n");
        } else {
            builder.append(">");
            builder.append(s.getCapabilities().get(CapabilityType.BROWSER_NAME));
            builder.append("</a>");
        }
        return builder.toString();
    }

    // the tabs header.
    private String nodeTabs() {
        String nodeTabs = "";
        nodeTabs = nodeTabs.concat("<div class='tabs'>");
        nodeTabs = nodeTabs.concat("<ul>");
        nodeTabs = nodeTabs.concat("<li class='tab' type='browsers'><a title='test slots' href='#'>Browsers</a></li>");
        nodeTabs = nodeTabs.concat("<li class='tab' type='config'><a title='node configuration' href='#'>Configuration</a></li>");
        nodeTabs = nodeTabs.concat("</ul></div>");
        return nodeTabs;
    }


    /**
     * return the platform for the proxy. It should be the same for all slots of the proxy, so checking that.
     * @param proxy remote proxy
     * @return Either the platform name, "Unknown", "mixed OS", or "not specified".
     */
    public static String getPlatform(RemoteProxy proxy) {
        Platform res;
        if (proxy.getTestSlots().isEmpty()) {
            return "Unknown";
        } else {
            res = getPlatform(proxy.getTestSlots().get(0));
        }

        for (TestSlot slot : proxy.getTestSlots()) {
            Platform tmp = getPlatform(slot);
            if (tmp != res) {
                return "mixed OS";
            } else {
                res = tmp;
            }
        }
        if (res == null) {
            return "not specified";
        } else {
            return res.toString();
        }
    }

    private static Platform getPlatform(TestSlot slot) {
        Object o = slot.getCapabilities().get(CapabilityType.PLATFORM);
        if (o == null) {
            return Platform.ANY;
        } else {
            if (o instanceof String) {
                return Platform.valueOf((String) o);
            } else if (o instanceof Platform) {
                return (Platform) o;
            } else {
                throw new GridException("Cannot cast " + o + " to org.openqa.selenium.Platform");
            }
        }
    }

}

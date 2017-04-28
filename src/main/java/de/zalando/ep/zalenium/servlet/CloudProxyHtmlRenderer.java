package de.zalando.ep.zalenium.servlet;

import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.proxy.BrowserStackRemoteProxy;
import de.zalando.ep.zalenium.proxy.SauceLabsRemoteProxy;
import de.zalando.ep.zalenium.proxy.TestingBotRemoteProxy;
import org.openqa.grid.common.exception.GridException;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.web.servlet.beta.MiniCapability;
import org.openqa.grid.web.servlet.beta.SlotsLines;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;

public class CloudProxyHtmlRenderer implements HtmlRenderer {

    private RemoteProxy proxy;

    @SuppressWarnings("unused")
    private CloudProxyHtmlRenderer() {}

    public CloudProxyHtmlRenderer(RemoteProxy proxy) {
        this.proxy = proxy;
    }



    public String renderSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div class='proxy'>");
        builder.append("<p class='proxyname'>");
        builder.append(proxy.getClass().getSimpleName());

        builder.append(getHtmlNodeVersion());

        String platform = getPlatform(proxy);

        builder.append("<p class='proxyid'>id : ");
        builder.append(proxy.getId());
        builder.append(", OS : " + platform + "</p>");

        builder.append(nodeTabs());

        builder.append("<div class='content'>");

        builder.append(tabBrowsers());
        builder.append(tabConfig());

        builder.append("</div>");
        builder.append("</div>");

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
            return " unknown version,"+e.getMessage();
        }
    }

    // content of the config tab.
    private String tabConfig() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div type='config' class='content_detail'>");
        builder.append(proxy.getConfig().toString("<p>%1$s: %2$s</p>"));
        builder.append("</div>");
        return builder.toString();
    }


    // content of the browsers tab
    private String tabBrowsers() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div type='browsers' class='content_detail'>");

        SlotsLines wdLines = new SlotsLines();

        for (TestSlot slot : proxy.getTestSlots()) {
            wdLines.add(slot);
        }

        if (wdLines.getLinesType().size() != 0) {
            builder.append("<p class='protocol' >WebDriver</p>");
            builder.append(getLines(wdLines));
        }
        builder.append("</div>");
        return builder.toString();
    }

    // the lines of icon representing the possible slots
    private String getLines(SlotsLines lines) {
        StringBuilder builder = new StringBuilder();
        for (MiniCapability cap : lines.getLinesType()) {
            String version = cap.getVersion();
            builder.append("<p>");
            if (version != null) {
                builder.append("v:" + version);
            }
            for (TestSlot s : lines.getLine(cap)) {
                builder.append(getSingleSlotHtml(s));
            }
            builder.append("</p>");
        }
        return builder.toString();
    }

    // icon ( or generic html if icon not available )
    private String getSingleSlotHtml(TestSlot s) {
        StringBuilder builder = new StringBuilder();
        TestSession session = s.getSession();
        String icon = "";
        if (proxy instanceof TestingBotRemoteProxy) {
            icon = "/grid/admin/ZaleniumResourceServlet/images/testingbot.png";
        }
        if (proxy instanceof BrowserStackRemoteProxy) {
            icon = "/grid/admin/ZaleniumResourceServlet/images/browserstack.png";
        }
        if (proxy instanceof SauceLabsRemoteProxy) {
            icon = "/grid/admin/ZaleniumResourceServlet/images/saucelabs.png";
        }
        builder.append("<img ");
        builder.append("src='").append(icon).append("' width='16' height='16'");

        if (session != null) {
            builder.append(" class='busy' ");
            builder.append(" title='").append(session.get("lastCommand")).append("' ");
        } else {
            builder.append(" title='").append(s.getCapabilities()).append("'");
        }
        builder.append(" />\n");
        return builder.toString();
    }

    // the tabs header.
    private String nodeTabs() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div class='tabs'>");
        builder.append("<ul>");
        builder
                .append("<li class='tab' type='browsers'><a title='test slots' href='#'>Browsers</a></li>");
        builder
                .append("<li class='tab' type='config'><a title='node configuration' href='#'>Configuration</a></li>");
        builder.append("</ul>");
        builder.append("</div>");
        return builder.toString();
    }


    /**
     * return the platform for the proxy. It should be the same for all slots of the proxy, so checking that.
     * @param proxy remote proxy
     * @return Either the platform name, "Unknown", "mixed OS", or "not specified".
     */
    public static String getPlatform(RemoteProxy proxy) {
        Platform res = null;
        if (proxy.getTestSlots().size() == 0) {
            return "Unknown";
        }
        res = getPlatform(proxy.getTestSlots().get(0));

        for (TestSlot slot : proxy.getTestSlots()) {
            Platform tmp = getPlatform(slot);
            if (tmp != res) {
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
        Object o = slot.getCapabilities().get(CapabilityType.PLATFORM);
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

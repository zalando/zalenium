package de.zalando.ep.zalenium.servlet;

import com.google.common.io.ByteStreams;
import de.zalando.ep.zalenium.servlet.renderer.TemplateRenderer;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.BuildInfo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
    Taken from the original org.openqa.grid.web.servlet.beta.ConsoleServlet
 */
public class ZaleniumConsoleServlet extends RegistryBasedServlet {
    private static String coreVersion;
    private TemplateRenderer templateRenderer;

    @SuppressWarnings("unused")
    public ZaleniumConsoleServlet() {
        this(null);
    }

    public ZaleniumConsoleServlet(GridRegistry registry) {
        super(registry);
        coreVersion = new BuildInfo().getReleaseLabel();
        String templateFile = "html_templates/zalenium_console_servlet.html";
        templateRenderer = new TemplateRenderer(templateFile);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        process(request, response);
    }

    protected void process(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Refreshing every 20 minutes by default
        int refresh = 1200;
        if (request.getParameter("refresh") != null) {
            refresh = Integer.parseInt(request.getParameter("refresh"));
        }

        List<String> nodes = new ArrayList<>();
        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            nodes.add(proxy.getHtmlRender().renderSummary());
        }

        int size = nodes.size();
        int rightColumnSize = size / 2;
        int leftColumnSize = size - rightColumnSize;

        StringBuilder leftColumnNodes = new StringBuilder();
        for (int i = 0; i < leftColumnSize; i++) {
            leftColumnNodes.append(nodes.get(i));
        }
        StringBuilder rightColumnNodes = new StringBuilder();
        for (int i = leftColumnSize; i < nodes.size(); i++) {
            rightColumnNodes.append(nodes.get(i));
        }

        String hubConfigLinkVisible = "";
        String hubConfigVisible = "hidden";
        if (request.getParameter("config") != null) {
            hubConfigLinkVisible = "hidden";
            hubConfigVisible = "";
        }

        Map<String, String> consoleValues = new HashMap<>();
        consoleValues.put("{{refreshInterval}}", String.valueOf(refresh));
        consoleValues.put("{{coreVersion}}", coreVersion);
        consoleValues.put("{{leftColumnNodes}}", leftColumnNodes.toString());
        consoleValues.put("{{rightColumnNodes}}", rightColumnNodes.toString());
        consoleValues.put("{{unprocessedRequests}}", getUnprocessedRequests());
        consoleValues.put("{{requestQueue}}", getRequestQueue());
        consoleValues.put("{{hubConfigLinkVisible}}", hubConfigLinkVisible);
        consoleValues.put("{{hubConfigVisible}}", hubConfigVisible);
        consoleValues.put("{{hubConfig}}", getConfigInfo(request.getParameter("configDebug") != null));

        String renderTemplate = templateRenderer.renderTemplate(consoleValues);

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(200);
        try (InputStream in = new ByteArrayInputStream(renderTemplate.getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }

    private String getUnprocessedRequests() {
        int numUnprocessedRequests = getRegistry().getNewSessionRequestCount();
        String unprocessedRequests = "";
        if (numUnprocessedRequests > 0) {
            unprocessedRequests = String.format("%d requests waiting for a slot to be free.", numUnprocessedRequests);
        }
        return unprocessedRequests;
    }

    private String getRequestQueue() {
        StringBuilder requestQueue = new StringBuilder();
        for (MutableCapabilities req : getRegistry().getDesiredCapabilities()) {
            Map<String, String> pendingRequest = new HashMap<>();
            pendingRequest.put("{{pendingRequest}}", req.toString());
            requestQueue.append(templateRenderer.renderSection("{{requestQueue}}", pendingRequest));
        }
        return requestQueue.toString();
    }

    /**
     * retracing how the hub config was built to help debugging.
     *
     * @return html representation of the hub config
     */
    private String getConfigInfo(boolean verbose) {

        GridHubConfiguration config = getRegistry().getHub().getConfiguration();
        Map<String, String> configInfoValues = new HashMap<>();
        configInfoValues.put("{{hubCurrentConfig}}", prettyHtmlPrint(config));

        String hubConfigVerboseVisible = "hidden";
        if (verbose) {
            hubConfigVerboseVisible = "";
            GridHubConfiguration tmp = new GridHubConfiguration();
            configInfoValues.put("{{hubDefaultConfig}}", prettyHtmlPrint(tmp));
            tmp.merge(config);
            configInfoValues.put("{{hubMergedConfig}}", prettyHtmlPrint(tmp));
        }
        configInfoValues.put("{{hubConfigVerboseVisible}}", hubConfigVerboseVisible);

        return templateRenderer.renderSection("{{hubConfig}}", configInfoValues);
    }

    private String prettyHtmlPrint(GridHubConfiguration config) {
        return config.toString("<abbr title='%1$s'>%1$s : </abbr>%2$s</br>");
    }

}

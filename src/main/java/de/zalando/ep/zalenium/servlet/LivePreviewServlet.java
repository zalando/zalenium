package de.zalando.ep.zalenium.servlet;

/*
    This class renders an HTML with a similar appearance to the Grid Console, it just adds an iFrame that
    allows users to see what is happening inside the container while they run their tests.
    The code here is based on the ConsoleServlet class from the Selenium Grid
 */

import com.google.common.io.ByteStreams;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.servlet.renderer.LiveNodeHtmlRenderer;
import de.zalando.ep.zalenium.servlet.renderer.TemplateRenderer;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.web.servlet.RegistryBasedServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LivePreviewServlet extends RegistryBasedServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivePreviewServlet.class.getName());

    @SuppressWarnings("unused")
    public LivePreviewServlet(){
        this(null);
    }

    public LivePreviewServlet(GridRegistry registry) {
        super(registry);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            process(request, response);
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            process(request, response);
        } catch (Exception e) {
            LOGGER.error(e.toString(), e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void process(HttpServletRequest request, HttpServletResponse response)
            throws IOException {


        String refresh = "1200";
        String testBuild = "";
        try {
            refresh = Optional.ofNullable(request.getParameter("refresh")).orElse(refresh);
            testBuild = Optional.ofNullable(request.getParameter("build")).orElse(testBuild);
        } catch (Exception e) {
            LOGGER.debug(e.toString(), e);
        }

        List<String> nodes = new ArrayList<>();
        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            if (proxy instanceof DockerSeleniumRemoteProxy) {
                DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;
                HtmlRenderer renderer = new LiveNodeHtmlRenderer(dockerSeleniumRemoteProxy);
                // Render the nodes that are part of an specified test build
                if (testBuild.isEmpty() || testBuild.equalsIgnoreCase(dockerSeleniumRemoteProxy.getTestBuild())) {
                    nodes.add(renderer.renderSummary());
                }
            }
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

        Map<String, String> livePreviewValues = new HashMap<>();
        livePreviewValues.put("{{refreshInterval}}", refresh);
        livePreviewValues.put("{{leftColumnNodes}}", leftColumnNodes.toString());
        livePreviewValues.put("{{rightColumnNodes}}", rightColumnNodes.toString());
        String templateFile = "html_templates/live_preview_servlet.html";
        TemplateRenderer templateRenderer = new TemplateRenderer(templateFile);
        String renderTemplate = templateRenderer.renderTemplate(livePreviewValues);

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(200);

        try (InputStream in = new ByteArrayInputStream(renderTemplate.getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }
}

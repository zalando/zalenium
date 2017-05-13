package de.zalando.ep.zalenium.servlet;

/*
    This class renders an HTML with a similar appearance to the Grid Console, it just adds an iFrame that
    allows users to see what is happening inside the container while they run their tests.
    The code here is based on the ConsoleServlet class from the Selenium Grid
 */

import com.google.common.io.ByteStreams;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.web.servlet.RegistryBasedServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LivePreviewServlet extends RegistryBasedServlet {

    private static final Logger LOGGER = Logger.getLogger(LivePreviewServlet.class.getName());

    @SuppressWarnings("unused")
    public LivePreviewServlet(){
        this(null);
    }

    public LivePreviewServlet(Registry registry) {
        super(registry);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            process(request, response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString(), e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            process(request, response);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString(), e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void process(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        int refresh = -1;
        String testGroup = "";

        try {
            refresh = Integer.parseInt(request.getParameter("refresh"));
            testGroup = request.getParameter("group") == null ? "" : request.getParameter("group");
        } catch (Exception e) {
            LOGGER.log(Level.FINE, e.toString(), e);
        }

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(200);

        StringBuilder builder = new StringBuilder();

        builder.append("<html>");
        builder.append("<head>");
        builder.append("<script src='/grid/resources/org/openqa/grid/images/jquery-3.1.1.min.js'></script>");
        builder.append("<script src='/grid/resources/org/openqa/grid/images/consoleservlet.js'></script>");
        builder.append("<link href='/grid/resources/org/openqa/grid/images/consoleservlet.css' rel='stylesheet' type='text/css' />");
        builder.append("<link href='/grid/resources/org/openqa/grid/images/favicon.ico' rel='icon' type='image/x-icon' />");

        if (refresh != -1) {
            builder.append(String.format("<meta http-equiv='refresh' content='%d' />", refresh));
        }

        builder.append("<title>Live Preview</title>");

        builder.append("<style>");
        builder.append(".busy {opacity : 0.4; filter: alpha(opacity=40);}");
        builder.append("</style>");
        builder.append("</head>");

        builder.append("<body>");

        builder.append("<div id='main_content'>");

        builder.append(getHeader());

        List<String> nodes = new ArrayList<>();
        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            if (proxy instanceof DockerSeleniumRemoteProxy) {
                DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;
                HtmlRenderer renderer = new LiveNodeHtmlRenderer(dockerSeleniumRemoteProxy, request.getServerName());
                // Render the nodes that are part of an specified test group
                if (testGroup.isEmpty() || testGroup.equalsIgnoreCase(dockerSeleniumRemoteProxy.getTestGroup())) {
                    nodes.add(renderer.renderSummary());
                }
            }
        }

        int size = nodes.size();
        int rightColumnSize = size / 2;
        int leftColumnSize = size - rightColumnSize;

        builder.append("<div id='left-column'>");
        for (int i = 0; i < leftColumnSize; i++) {
            builder.append(nodes.get(i));
        }
        builder.append("</div>");

        builder.append("<div id='right-column'>");
        for (int i = leftColumnSize; i < nodes.size(); i++) {
            builder.append(nodes.get(i));
        }
        builder.append("</div></div>");
        builder.append("</body>");
        builder.append("</html>");

        try (InputStream in = new ByteArrayInputStream(builder.toString().getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }

    private Object getHeader() {
        String header = "";
        header = header.concat("<div id='header'>");
        header = header.concat("<h1><a href='/grid/LivePreviewServlet'>Zalenium Live Preview</a></h1>");
        header = header.concat("<h2>Zalenium Live Preview");
        header = header.concat("</h2>");
        header = header.concat("<div><a id='helplink' target='_blank' ");
        header = header.concat("href='https://github.com/zalando/zalenium'>Help</a></div></div>");
        return header;
    }

}

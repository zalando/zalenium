package de.zalando.ep.zalenium.servlet;

import com.google.common.io.ByteStreams;
import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.web.servlet.RegistryBasedServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CopyProxyServlet extends RegistryBasedServlet {

    private static final Logger LOGGER = Logger.getLogger(CopyProxyServlet.class.getName());

    @SuppressWarnings("unused")
    public CopyProxyServlet(){
        this(null);
    }

    public CopyProxyServlet(Registry registry) {
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

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(302);
        response.setHeader("Location", "/grid/admin/live");

        createProxyFromExisting(request.getParameter("id"));

        try (InputStream in = new ByteArrayInputStream("ok".getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }

    private void createProxyFromExisting(String id) {
        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            if (proxy instanceof DockerSeleniumStarterRemoteProxy) {
                DockerSeleniumStarterRemoteProxy starterProxy = (DockerSeleniumStarterRemoteProxy) proxy;
                starterProxy.getNewSession(getRequestedCapability(id));
                break;
            }
        }
    }

    private HashMap<String, Object> getRequestedCapability(String id) {
        RemoteProxy proxy = getRegistry().getProxyById(id);
        if (proxy == null) {
            throw new IllegalArgumentException("illegal id");
        }

        TestSlot slot = proxy.getTestSlots().get(0);
        if (slot == null) {
            throw new IllegalArgumentException("Proxy has no test slots");
        }

        return new HashMap<>(slot.getCapabilities());
    }
}

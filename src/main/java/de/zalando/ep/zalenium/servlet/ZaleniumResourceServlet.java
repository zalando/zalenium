package de.zalando.ep.zalenium.servlet;

import com.google.common.io.ByteStreams;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;

/*
    Taken from the original org.openqa.grid.web.servlet.ResourceServlet
 */

public class ZaleniumResourceServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }

    protected void process(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String resource = request.getPathInfo().replace(request.getServletPath(), "");
        if (resource.startsWith("/"))
            resource = resource.replaceFirst("/", "");
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new Error("Cannot find resource " + resource);
        }

        try {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            in.close();
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            c.add(Calendar.DATE, 10);
            response.setDateHeader("Expires", c.getTime().getTime());
            response.setHeader("Cache-Control", "max-age=864000");
            response.flushBuffer();
        }

    }
}

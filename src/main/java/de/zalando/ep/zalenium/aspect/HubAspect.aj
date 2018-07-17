package de.zalando.ep.zalenium.aspect;


import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.openqa.grid.web.Hub;
import org.seleniumhq.jetty9.servlet.FilterHolder;
import org.seleniumhq.jetty9.servlet.FilterMapping;
import org.seleniumhq.jetty9.servlet.ServletContextHandler;
import org.seleniumhq.jetty9.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.zalando.ep.zalenium.dashboard.DashboardCleanupServlet;
import de.zalando.ep.zalenium.dashboard.DashboardInformationServlet;
import de.zalando.ep.zalenium.servlet.LivePreviewServlet;
import de.zalando.ep.zalenium.servlet.VncAuthenticationServlet;
import de.zalando.ep.zalenium.servlet.ZaleniumConsoleServlet;
import de.zalando.ep.zalenium.servlet.ZaleniumResourceServlet;
import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.filter.MetricsFilter;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.jetty.JettyStatisticsCollector;

import static de.zalando.ep.zalenium.util.ZaleniumConfiguration.ZALENIUM_RUNNING_LOCALLY;

privileged public aspect HubAspect {
    private static final Logger log = LoggerFactory.getLogger(HubAspect.class.getName());

    pointcut callAddDefaultServlets(ServletContextHandler handler, Hub hub) :
        call (private void Hub.addDefaultServlets(ServletContextHandler)) && args(handler) && target(hub);
    
    before(ServletContextHandler handler, Hub hub) : callAddDefaultServlets(handler, hub) {
        log.info("Registering custom Zalenium servlets");

        if (!ZALENIUM_RUNNING_LOCALLY) {
            // This crazy casting is to get around the fact that jetty has been repackaged by selenium, at runtime this will work.
            Server server = (Server)((Object)hub.server);
            initialisePrometheus(handler, server);
        }

        registerZaleniumServlets(handler);
    }
    
    protected void initialisePrometheus(ServletContextHandler handler, Server server) {
        EnumSet<DispatcherType> allDispatchers = EnumSet.allOf(DispatcherType.class);
        FilterHolder prometheus = handler.addFilter(MetricsFilter.class, "/wd/*", allDispatchers);
        prometheus.setInitParameter("metric-name", "webapp_metrics_filter");
        prometheus.setInitParameter("help", "This is the help for your metrics filter");
        prometheus.setInitParameter("buckets", "0.005,0.01,0.025,0.05,0.075,0.1,0.25,0.5,0.75,1,2.5,5,7.5,10");
        // We don't want to go past 3 path-components otherwise every selenium session ends up in the metrics.
        prometheus.setInitParameter("path-components", "3");

        // Add extra mappings
        addFilterMappings(handler, prometheus.getName(), new String[] {"/grid/*", "/vnc/*", "/dashboard/*"}, allDispatchers);
        
        // Enable prometheus metrics at /metrics
        handler.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
        
        // Configure StatisticsHandler.
        StatisticsHandler stats = new StatisticsHandler();
        stats.setHandler(server.getHandler());
        server.setHandler(stats);
        // Register collector.
        new JettyStatisticsCollector(stats).register();
        
        DefaultExports.initialize();
    }
    
    protected void addFilterMappings(ServletContextHandler handler, String filterName, String[] pathSpecs, EnumSet<DispatcherType> dispatchers) {
        FilterMapping m = new FilterMapping();
        m.setFilterName(filterName);
        m.setDispatcherTypes(dispatchers);
        m.setPathSpecs(pathSpecs);
        handler.getServletHandler().addFilterMapping(m);
    }
    
    protected void registerZaleniumServlets(ServletContextHandler handler) {
        handler.addServlet(LivePreviewServlet.class, "/grid/admin/live");
        handler.addServlet(ZaleniumConsoleServlet.class, "/grid/console");
        handler.addServlet(ZaleniumResourceServlet.class, "/resources/*");
        handler.addServlet(DashboardCleanupServlet.class, "/dashboard/cleanup");
        handler.addServlet(DashboardInformationServlet.class, "/dashboard/information");
        handler.addServlet(VncAuthenticationServlet.class, "/vnc/auth");
    }
}
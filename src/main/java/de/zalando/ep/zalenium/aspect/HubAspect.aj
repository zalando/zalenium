package de.zalando.ep.zalenium.aspect;


import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.openqa.grid.web.Hub;
import org.seleniumhq.jetty9.servlet.FilterHolder;
import org.seleniumhq.jetty9.servlet.ServletContextHandler;
import org.seleniumhq.jetty9.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.filter.MetricsFilter;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.jetty.JettyStatisticsCollector;

privileged public aspect HubAspect {
    private static final Logger log = LoggerFactory.getLogger(HubAspect.class.getName());

    pointcut callAddDefaultServlets(ServletContextHandler handler, Hub hub) :
        call (private void Hub.addDefaultServlets(ServletContextHandler)) && args(handler) && target(hub);
    
    before(ServletContextHandler handler, Hub hub) : callAddDefaultServlets(handler, hub) {
        log.info("Registering custom Zalenium servlets");
        
        FilterHolder prometheusMetricsFilter = handler.addFilter(MetricsFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        prometheusMetricsFilter.setInitParameter("metric-name", "webapp_metrics_filter");
        prometheusMetricsFilter.setInitParameter("help", "This is the help for your metrics filter");
        prometheusMetricsFilter.setInitParameter("buckets", "0.005,0.01,0.025,0.05,0.075,0.1,0.25,0.5,0.75,1,2.5,5,7.5,10");
        
        // Enable prometheus metrics at /metrics
        handler.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
        
        // Configure StatisticsHandler.
        
        // This crazy casting is to get around the fact that jetty has been repackaged by selenium, at runtime this will work.
        Server server = (Server)((Object)hub.server);
        StatisticsHandler stats = new StatisticsHandler();
        stats.setHandler(server.getHandler());
        server.setHandler(stats);
        // Register collector.
        new JettyStatisticsCollector(stats).register();
        
        DefaultExports.initialize();
    }
}
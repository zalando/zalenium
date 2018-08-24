package de.zalando.ep.zalenium.prometheus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.RemoteProxy;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;

public class TestSessionCollectorExports extends Collector {

    private ProxySet proxySet;

    public TestSessionCollectorExports(ProxySet proxySet) {
        super();
        this.proxySet = proxySet;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        GaugeMetricFamily testSessionMetric = new GaugeMetricFamily("selenium_test_sessions_running",
                "The number of Selenium test sessions that are running by proxy type",
                Collections.singletonList("proxy"));

        Iterable<RemoteProxy> iterable = () -> proxySet.iterator();
        Map<String, Integer> countByProxies = StreamSupport.stream(iterable.spliterator(), false).collect(
                Collectors.groupingBy(p -> p.getClass().getSimpleName(), Collectors.summingInt(p -> p.getTotalUsed())));
        
        countByProxies.entrySet().stream()
                .forEach(e -> testSessionMetric.addMetric(Collections.singletonList(e.getKey()), e.getValue()));

        List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
        mfs.add(testSessionMetric);
        return mfs;
    }

}

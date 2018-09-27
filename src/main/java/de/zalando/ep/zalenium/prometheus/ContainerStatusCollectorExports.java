package de.zalando.ep.zalenium.prometheus;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.proxy.AutoStartProxySet.ContainerStatus;
import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;

public class ContainerStatusCollectorExports extends Collector {
    
    private enum States {
        RUNNING,
        STARTING;
    }

    private Map<ContainerCreationStatus, ContainerStatus> startedContainers;
    private static List<States> STATES = Arrays.asList(States.values());

    public ContainerStatusCollectorExports(Map<ContainerCreationStatus, ContainerStatus> startedContainers) {
        super();
        this.startedContainers = startedContainers;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        
        GaugeMetricFamily seleniumContainersStateMetric = new GaugeMetricFamily("selenium_containers",
                "The number of Selenium Containers broken down by state",
                singletonList("state"));
        
        Map<States, Long> containerStates = startedContainers.values().stream()
                .collect(groupingBy(s -> {
                    return s.isStarted() ? States.RUNNING : States.STARTING;
                }, counting()));
        
        // Ensure that if a state is empty it is 0 instead of missing.
        STATES.stream().forEach(s -> 
            seleniumContainersStateMetric.addMetric(singletonList(s.name().toLowerCase()), 
                Optional.ofNullable(containerStates.get(s)).orElse(0L)));

        List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>();
        mfs.add(seleniumContainersStateMetric);
        return mfs;
    }

}

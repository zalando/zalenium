package de.zalando.ep.zalenium.prometheus;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.proxy.AutoStartProxySet.ContainerStatus;
import io.prometheus.client.CollectorRegistry;

public class ContainerStatusCollectorExportsTest {

    @Before
    @After
    public void resetPrometheus() {
        CollectorRegistry.defaultRegistry.clear();
    }
    
    @Test
    public void testEmptyMapBothValuesZero() {
        Map<ContainerCreationStatus,ContainerStatus> map = new HashMap<ContainerCreationStatus, ContainerStatus>();
        new ContainerStatusCollectorExports(map).register(CollectorRegistry.defaultRegistry);
        
        Double startingValue = CollectorRegistry.defaultRegistry.getSampleValue("selenium_containers", new String[] {"state"}, new String[] {"starting"});
        Double runningValue = CollectorRegistry.defaultRegistry.getSampleValue("selenium_containers", new String[] {"state"}, new String[] {"running"});
        assertThat(startingValue, equalTo(0.0));
        assertThat(runningValue, equalTo(0.0));
    }
    
    @Test
    public void testIncrementChanges() {
        // Given an empty map
        Map<ContainerCreationStatus,ContainerStatus> map = new HashMap<ContainerCreationStatus, ContainerStatus>();
        new ContainerStatusCollectorExports(map).register(CollectorRegistry.defaultRegistry);
                
        // We get an empty values for both states
        Double startingValue = CollectorRegistry.defaultRegistry.getSampleValue("selenium_containers", new String[] {"state"}, new String[] {"starting"});
        Double runningValue = CollectorRegistry.defaultRegistry.getSampleValue("selenium_containers", new String[] {"state"}, new String[] {"running"});
        assertThat(startingValue, equalTo(0.0));
        assertThat(runningValue, equalTo(0.0));
        
        // After we add 2 starting containers
        ContainerStatus container1 = new ContainerStatus("123", 0l);
        ContainerStatus container2 = new ContainerStatus("1234", 0l);
        
        map.put(mock(ContainerCreationStatus.class), container1);
        map.put(mock(ContainerCreationStatus.class), container2);
        
        // We expect 2 starting and 0 running
        startingValue = CollectorRegistry.defaultRegistry.getSampleValue("selenium_containers", new String[] {"state"}, new String[] {"starting"});
        runningValue = CollectorRegistry.defaultRegistry.getSampleValue("selenium_containers", new String[] {"state"}, new String[] {"running"});
        assertThat(startingValue, equalTo(2.0));
        assertThat(runningValue, equalTo(0.0));
        
        // Once we add a started time
        container1.setTimeStarted(Optional.of(0l));
        container2.setTimeStarted(Optional.of(0l));

        // We expect 0 starting and 2 running
        startingValue = CollectorRegistry.defaultRegistry.getSampleValue("selenium_containers", new String[] {"state"}, new String[] {"starting"});
        runningValue = CollectorRegistry.defaultRegistry.getSampleValue("selenium_containers", new String[] {"state"}, new String[] {"running"});
        assertThat(startingValue, equalTo(0.0));
        assertThat(runningValue, equalTo(2.0));
    }
}

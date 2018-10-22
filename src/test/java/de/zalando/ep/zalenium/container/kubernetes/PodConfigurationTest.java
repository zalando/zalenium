package de.zalando.ep.zalenium.container.kubernetes;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HostAlias;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PodConfigurationTest {

    private PodConfiguration podConfiguration;

    @BeforeTest
    public void prepare() {
        podConfiguration = new PodConfiguration();
    }

    @Test
    public void setNodePort() {
        podConfiguration.setNodePort("test");
        assertThat(podConfiguration.getNodePort(), containsString("test"));
    }

    @Test
    public void setKubernetesClient() {
        KubernetesClient client = mock(KubernetesClient.class);
        podConfiguration.setClient(client);
        assertThat(podConfiguration.getClient(), is(client));
    }

    @Test
    public void setContainerIdPrefix() {
        podConfiguration.setContainerIdPrefix("test");
        assertThat(podConfiguration.getContainerIdPrefix(), containsString("test"));
    }

    @Test
    public void setImage() {
        podConfiguration.setImage("test");
        assertThat(podConfiguration.getImage(), containsString("test"));
    }

    @Test
    public void setEnvVars() {
        EnvVar envVar = mock(EnvVar.class);
        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(envVar);
        podConfiguration.setEnvVars(envVars);
        assertThat(podConfiguration.getEnvVars().size(), is(1));
        assertThat(podConfiguration.getEnvVars(), is(envVars));
    }

    @Test
    public void setLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("key", "value");
        podConfiguration.setLabels(labels);
        assertThat(podConfiguration.getLabels().get("key"), containsString("value"));
    }

    @Test
    public void setPodLimits() {
        Map<String, Quantity> limits = new HashMap<>();
        Quantity quantity = mock(Quantity.class);
        limits.put("key", quantity);
        podConfiguration.setPodLimits(limits);
        assertThat(podConfiguration.getPodLimits().get("key"), is(quantity));
    }

    @Test
    public void setPodRequests() {
        Map<String, Quantity> requests = new HashMap<>();
        Quantity quantity = mock(Quantity.class);
        requests.put("key", quantity);
        podConfiguration.setPodRequests(requests);
        assertThat(podConfiguration.getPodRequests().get("key"), is(quantity));
    }

    @Test
    public void setMountedSharedFoldersMap() {
        Map<VolumeMount, Volume> mountedSharedFoldersMap = new HashMap<>();
        VolumeMount volumeMount = mock(VolumeMount.class);
        Volume volume = mock(Volume.class);
        mountedSharedFoldersMap.put(volumeMount, volume);
        podConfiguration.setMountedSharedFoldersMap(mountedSharedFoldersMap);
        assertThat(podConfiguration.getMountedSharedFoldersMap().get(volumeMount), is(volume));
    }

    @Test
    public void setHostAliases() {
        List<HostAlias> hostAliases = new ArrayList<>();
        HostAlias hostAlias = mock(HostAlias.class);
        hostAliases.add(hostAlias);
        podConfiguration.setHostAliases(hostAliases);
        assertThat(podConfiguration.getHostAliases().size(), is(1));
        assertThat(podConfiguration.getHostAliases().get(0), is(hostAlias));
    }

    @Test
    public void setNodeSelector() {
        Map<String, String> nodeSelector = new HashMap<>();
        nodeSelector.put("key", "value");
        podConfiguration.setNodeSelector(nodeSelector);
        assertThat(podConfiguration.getNodeSelector().get("key"), containsString("value"));
    }

    @Test
    public void setTolerations() {
        List<Toleration> tolerations = new ArrayList<>();
        Toleration toleration = mock(Toleration.class);
        tolerations.add(toleration);
        podConfiguration.setTolerations(tolerations);
        assertThat(podConfiguration.getTolerations().size(), is(1));
        assertThat(podConfiguration.getTolerations().get(0), is(toleration));
    }
}

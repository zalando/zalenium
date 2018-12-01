package de.zalando.ep.zalenium.proxy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;
import org.openqa.grid.internal.TestSession;
import org.testng.Assert;

import de.zalando.ep.zalenium.container.ContainerCreationStatus;

public class AutoStartProxySetTest {

    @Test
    public void containersAreStartedWhenSetIsEmpty() {
        // Given a proxy set that will not autostart containers.
        Clock clock = Clock.fixed(Instant.ofEpochMilli(10000), ZoneId.systemDefault());

        DockeredSeleniumStarter starter = Mockito.mock(DockeredSeleniumStarter.class);
        ContainerCreationStatus containerCreationStatus = new ContainerCreationStatus(true, "name", "id", "40000");
        Mockito.when(starter.startDockerSeleniumContainer(Mockito.any())).thenReturn(containerCreationStatus);

        AutoStartProxySet autoStartProxySet = new AutoStartProxySet(false, 0, 5, 1000, false, starter, clock, 30);

        // When a new session is requested.
        TestSession session = autoStartProxySet.getNewSession(Collections.emptyMap());

        // Then a null should have been returned - no session was allocated.
        Assert.assertNull(session, "No session should have been created when proxy set is empty");

        // And a request should have been made to start a proxy.
        Mockito.verify(starter).startDockerSeleniumContainer(Collections.emptyMap());
    }

    @Test
    public void containersAreStartedWhenProxiesAreBusy() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(10000), ZoneId.systemDefault());

        DockeredSeleniumStarter starter = Mockito.mock(DockeredSeleniumStarter.class);
        ContainerCreationStatus containerCreationStatus = new ContainerCreationStatus(true, "name", "id", "40000");
        Mockito.when(starter.startDockerSeleniumContainer(Mockito.any())).thenReturn(containerCreationStatus);

        // Given a proxy set that contains a busy proxy.
        AutoStartProxySet autoStartProxySet = new AutoStartProxySet(false, 0, 5, 1000, false, starter, clock, 30);
        autoStartProxySet.add(proxy("busy_container", true));

        // When a new session is requested.
        TestSession session = autoStartProxySet.getNewSession(Collections.emptyMap());

        // Then a null should have been returned - no session was allocated.
        Assert.assertNull(session, "No session should have been created when proxy set is empty");

        // And a request should have been made to start a proxy.
        Mockito.verify(starter).startDockerSeleniumContainer(Collections.emptyMap());
    }

    @Test
    public void containersAreCapped() {

        Map<String, Object> session1 = Collections.singletonMap("session", "1");
        Map<String, Object> session2 = Collections.singletonMap("session", "2");
        Map<String, Object> session3 = Collections.singletonMap("session", "3");

        Clock clock = Clock.fixed(Instant.ofEpochMilli(10000), ZoneId.systemDefault());

        DockeredSeleniumStarter starter = Mockito.mock(DockeredSeleniumStarter.class);

        Mockito.when(starter.startDockerSeleniumContainer(session1))
                .thenReturn(new ContainerCreationStatus(true, "name", "id_1", "40000"));
        Mockito.when(starter.startDockerSeleniumContainer(session2))
                .thenReturn(new ContainerCreationStatus(true, "name", "id_2", "40000"));

        AutoStartProxySet autoStartProxySet = new AutoStartProxySet(false, 0, 2, 1000, false, starter, clock, 30);

        // When a new session is requested.
        Assert.assertNull(autoStartProxySet.getNewSession(session1));
        Mockito.verify(starter).startDockerSeleniumContainer(session1);

        Assert.assertNull(autoStartProxySet.getNewSession(session2));
        Mockito.verify(starter).startDockerSeleniumContainer(session2);

        TestSession newSession = autoStartProxySet.getNewSession(session3);
        Assert.assertNull(newSession);
        Mockito.verify(starter, Mockito.times(0)).startDockerSeleniumContainer(session3);
    }

    @Test
    public void requestsAreFiltered() {
        // Given a proxy set that will not autostart containers.
        Clock clock = Clock.fixed(Instant.ofEpochMilli(10000), ZoneId.systemDefault());

        DockeredSeleniumStarter starter = Mockito.mock(DockeredSeleniumStarter.class);
        ContainerCreationStatus containerCreationStatus = new ContainerCreationStatus(true, "name", "id", "40000");
        Mockito.when(starter.startDockerSeleniumContainer(Mockito.any())).thenReturn(containerCreationStatus);

        AutoStartProxySet autoStartProxySet = new AutoStartProxySet(false, 0, 5, 1000, false, starter, clock, 30);

        // When a new session is requested.
        TestSession session = autoStartProxySet.getNewSession(Collections.emptyMap());

        // Then a null should have been returned - no session was allocated.
        Assert.assertNull(session, "No session should have been created when proxy set is empty");

        // And a request should have been made to start a proxy.
        Mockito.verify(starter).startDockerSeleniumContainer(Collections.emptyMap());

        autoStartProxySet.getNewSession(Collections.emptyMap());

        Mockito.verify(starter, Mockito.times(1)).startDockerSeleniumContainer(Collections.emptyMap());
    }

    @Test
    public void containersAreMadeAvailable() {
        // Given a proxy set that will not autostart containers.
        Clock clock = Clock.fixed(Instant.ofEpochMilli(10000), ZoneId.systemDefault());

        DockerSeleniumRemoteProxy proxy = proxy("container_id", false);

        DockeredSeleniumStarter starter = Mockito.mock(DockeredSeleniumStarter.class);
        ContainerCreationStatus containerCreationStatus = new ContainerCreationStatus(true, "name", "container_id",
                "40000");
        Mockito.when(starter.startDockerSeleniumContainer(Mockito.any())).thenReturn(containerCreationStatus);

        AutoStartProxySet autoStartProxySet = new AutoStartProxySet(false, 0, 5, 1000, false, starter, clock, 30);

        // When a new session is requested.
        TestSession session = autoStartProxySet.getNewSession(Collections.emptyMap());

        // Then a null should have been returned - no session was allocated.
        Assert.assertNull(session, "No session should have been created when proxy set is empty");

        // And a request should have been made to start a proxy.
        Mockito.verify(starter).startDockerSeleniumContainer(Collections.emptyMap());

        // Then when the proxy is registered.
        autoStartProxySet.add(proxy);

        // And another test session is requested.
        TestSession postRegisterSession = autoStartProxySet.getNewSession(Collections.emptyMap());

        // Then a session should have been returned.
        Assert.assertNotNull(postRegisterSession, "Session should have been created after the proxy was registered.");
    }

    @Test
    public void containersAreStoppedWhenProxiesAreRemoved() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(10000), ZoneId.systemDefault());
        DockerSeleniumRemoteProxy proxy = proxy("container_id", false);
        DockeredSeleniumStarter starter = Mockito.mock(DockeredSeleniumStarter.class);
        ContainerCreationStatus containerCreationStatus = new ContainerCreationStatus(true, "name", "container_id",
                "40000");
        Mockito.when(starter.startDockerSeleniumContainer(Mockito.any())).thenReturn(containerCreationStatus);
        
        AutoStartProxySet autoStartProxySet = new AutoStartProxySet(false, 0, 5, 1000, false, starter, clock, 30);

        autoStartProxySet.getNewSession(Collections.emptyMap());
        autoStartProxySet.add(proxy);
        autoStartProxySet.remove(proxy);

        Mockito.verify(starter).stopContainer("container_id");
    }

    @Test
    public void orphanedContainersAreMarkedDown() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(10000), ZoneId.systemDefault());
        DockeredSeleniumStarter starter = Mockito.mock(DockeredSeleniumStarter.class);
        AutoStartProxySet autoStartProxySet = new AutoStartProxySet(false, 0, 5, 1000, false, starter, clock, 30);

        DockerSeleniumRemoteProxy proxy = proxy("container_id", false);

        autoStartProxySet.add(proxy);

        Mockito.verify(proxy).markDown();
    }

    private static DockerSeleniumRemoteProxy proxy(String containerId, boolean isBusy) {
        DockerSeleniumRemoteProxy remoteProxy = Mockito.mock(DockerSeleniumRemoteProxy.class);
        Mockito.when(remoteProxy.getContainerId()).thenReturn(containerId);
        if (isBusy) {
            Mockito.when(remoteProxy.getNewSession(Mockito.any())).thenReturn(null);
        } else {
            Mockito.when(remoteProxy.getNewSession(Mockito.any()))
                    .thenReturn(new TestSession(null, null, Clock.systemDefaultZone()));
        }
        return remoteProxy;
    }
}

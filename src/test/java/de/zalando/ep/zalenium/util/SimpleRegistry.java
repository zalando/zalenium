package de.zalando.ep.zalenium.util;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.mockito.Mockito;
import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.SessionTerminationReason;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.Hub;
import org.openqa.grid.web.servlet.handler.RequestHandler;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.http.HttpClient;

import okhttp3.OkHttpClient;

public final class SimpleRegistry implements GridRegistry {

    private final ProxySet proxySet = new ProxySet(false);

    @Override
    public void terminate(TestSession session, SessionTerminationReason reason) {
        // Not needed for current testing.
    }

    @Override
    public void removeIfPresent(RemoteProxy proxy) {
        if (proxySet.contains(proxy)) {
            proxySet.remove(proxy);
        }
    }

    @Override
    public void forceRelease(TestSlot testSlot, SessionTerminationReason reason) {
        // Not needed for current testing.
    }

    @Override
    public Hub getHub() {
        Hub hub = Mockito.mock(Hub.class);
        Mockito.when(hub.getConfiguration()).thenReturn(new GridHubConfiguration());
        return hub;
    }

    @Override
    public void setHub(Hub hub) {
        // Not needed for current testing.
    }

    @Override
    public void addNewSessionRequest(RequestHandler requestHandler) {
        // Not needed for current testing.
    }

    @Override
    public void add(RemoteProxy proxy) {
        proxySet.add(proxy);
    }

    @Override
    public void setThrowOnCapabilityNotPresent(boolean throwOnCapabilityNotPresent) {
        // Not needed for current testing.
    }

    @Override
    public ProxySet getAllProxies() {
        return proxySet;
    }

    @Override
    public List<RemoteProxy> getUsedProxies() {
        return proxySet.getBusyProxies();
    }

    @Override
    public TestSession getSession(ExternalSessionKey externalKey) {
        return null;
    }

    @Override
    public TestSession getExistingSession(ExternalSessionKey externalKey) {
        return null;
    }

    @Override
    public int getNewSessionRequestCount() {
        return 0;
    }

    @Override
    public void start() {
        // Not needed for current testing.
    }

    @Override
    public Iterable<DesiredCapabilities> getDesiredCapabilities() {
        return Collections.emptyList();
    }

    @Override
    public Set<TestSession> getActiveSessions() {
        return Collections.emptySet();
    }

    @Override
    public RemoteProxy getProxyById(String id) {
        return proxySet.getProxyById(id);
    }

    @Override
    public void stop() {
        // Not needed for current testing.
    }

    @Override
    public HttpClient getHttpClient(URL url) {
        return new org.openqa.selenium.remote.internal.OkHttpClient(new OkHttpClient(), url);
    }

    @Override
    public boolean removeNewSessionRequest(RequestHandler requestHandler) {
        return false;
    }

    @Override
    public void clearNewSessionRequests() {
        // Not needed for current testing.
    }

}
package de.zalando.ep.zalenium.registry;

import de.zalando.ep.zalenium.prometheus.ContainerStatusCollectorExports;
import de.zalando.ep.zalenium.prometheus.TestSessionCollectorExports;
import net.jcip.annotations.ThreadSafe;

import org.openqa.grid.internal.ActiveTestSessions;
import org.openqa.grid.internal.BaseGridRegistry;
import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.NewSessionRequestQueue;
import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.SessionTerminationReason;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.listeners.RegistrationListener;
import org.openqa.grid.internal.listeners.SelfHealingProxy;
import org.openqa.grid.web.Hub;
import org.openqa.grid.web.servlet.handler.RequestHandler;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.http.HttpClient;
import org.openqa.selenium.remote.server.log.LoggingManager;

import de.zalando.ep.zalenium.proxy.AutoStartProxySet;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockeredSeleniumStarter;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;
import io.prometheus.client.Collector;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

import java.net.URL;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Kernel of the grid. Keeps track of what's happening, what's free/used and assigns resources to
 * incoming requests.
 */
@SuppressWarnings("WeakerAccess")
@ThreadSafe
public class ZaleniumRegistry extends BaseGridRegistry implements GridRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(ZaleniumRegistry.class.getName());
    // lock for anything modifying the tests session currently running on this
    // registry.
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition testSessionAvailable = lock.newCondition();
    private final ProxySet proxies;
    private final ActiveTestSessions activeTestSessions = new ActiveTestSessions();
    private final NewSessionRequestQueue newSessionQueue;
    private final Matcher matcherThread = new Matcher();
    private final Set<RemoteProxy> registeringProxies = ConcurrentHashMap.newKeySet();
    private volatile boolean stop = false;
    
    private static final Environment defaultEnvironment = new Environment();
    
    private static final double[] DEFAULT_TEST_SESSION_LATENCY_BUCKETS =
            new double[] { 0.5,2.5,5,10,15,20,25,30,35,40,50,60 };

    // Allows overriding of the test session latency buckets for prometheus
    private static final String ZALENIUM_TEST_SESSION_LATENCY_BUCKETS = "ZALENIUM_TEST_SESSION_LATENCY_BUCKETS";
    
    static final Gauge seleniumTestSessionsWaiting = Gauge.build()
            .name("selenium_test_sessions_waiting").help("The number of Selenium test sessions that are waiting for a container").register();
    
    static final Histogram seleniumTestSessionStartLatency = Histogram.build()
            .name("selenium_test_session_start_latency_seconds")
            .help("The Selenium test session start time latency in seconds.")
            .buckets(defaultEnvironment.getDoubleArrayEnvVariable(ZALENIUM_TEST_SESSION_LATENCY_BUCKETS, DEFAULT_TEST_SESSION_LATENCY_BUCKETS))
            .register();

    @SuppressWarnings("unused")
    public ZaleniumRegistry() {
        this(null);
    }

    public ZaleniumRegistry(Hub hub) {
        super(hub);
        this.newSessionQueue = new NewSessionRequestQueue();
        
        long minContainers = ZaleniumConfiguration.getDesiredContainersOnStartup();
        long maxContainers = ZaleniumConfiguration.getMaxDockerSeleniumContainers();
        long timeToWaitToStart = ZaleniumConfiguration.getTimeToWaitToStart();
        boolean waitForAvailableNodes = ZaleniumConfiguration.isWaitForAvailableNodes();
        int maxTimesToProcessRequest = ZaleniumConfiguration.getMaxTimesToProcessRequest();

        DockeredSeleniumStarter starter = new DockeredSeleniumStarter();

        AutoStartProxySet autoStart = new AutoStartProxySet(false, minContainers, maxContainers, timeToWaitToStart,
            waitForAvailableNodes, starter, Clock.systemDefaultZone(), maxTimesToProcessRequest);
        proxies = autoStart;
        this.matcherThread.setUncaughtExceptionHandler(new UncaughtExceptionHandler());
        
        new TestSessionCollectorExports(proxies).register();
        new ContainerStatusCollectorExports(autoStart.getStartedContainers()).register();
    }

    /**
     * Creates a new {@link GridRegistry} and starts it.
     *
     * @param hub the {@link Hub} to associate this registry with
     * @param proxySet the {@link ProxySet} to manage proxies with
     */
    public ZaleniumRegistry(Hub hub, ProxySet proxySet) {
        super(hub);
        this.newSessionQueue = new NewSessionRequestQueue();
        proxies = proxySet;
        this.matcherThread.setUncaughtExceptionHandler(new UncaughtExceptionHandler());
    }

    /**
     * Creates a new {@link GridRegistry} and starts it.
     *
     * @param hub the {@link Hub} to associate this registry with
     * @return the registry
     */
    @SuppressWarnings("unused")
    public static GridRegistry newInstance(Hub hub) {
        ZaleniumRegistry registry = new ZaleniumRegistry(hub);
        registry.start();
        return registry;
    }

    /**
     * Creates a new {@link GridRegistry} and starts it.
     *
     * @param hub the {@link Hub} to associate this registry with
     * @param proxySet the {@link ProxySet} to manage proxies with
     * @return the registry
     */
    public static GridRegistry newInstance(Hub hub, ProxySet proxySet) {
        ZaleniumRegistry registry = new ZaleniumRegistry(hub, proxySet);
        registry.start();
        return registry;
    }

    public void start() {
        matcherThread.start();

        // freynaud : TODO
        // Grid registry is in a valid state when testSessionAvailable.await(); from
        // assignRequestToProxy is reached. Not before.
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Ends this test session for the hub, releasing the resources in the hub / registry. It does not
     * release anything on the remote. The resources are released in a separate thread, so the call
     * returns immediately. It allows release with long duration not to block the test while the hub is
     * releasing the resource.
     *
     * @param session The session to terminate
     * @param reason  the reason for termination
     */
    public void terminate(final TestSession session, final SessionTerminationReason reason) {
        // Thread safety reviewed
        String remoteName = "";
        if (session.getSlot().getProxy() instanceof DockerSeleniumRemoteProxy) {
            remoteName = ((DockerSeleniumRemoteProxy)session.getSlot().getProxy()).getRegistration().getContainerId();
        }
        String internalKey = Optional.ofNullable(session.getInternalKey()).orElse("No internal key");
        ExternalSessionKey externalKey = Optional.ofNullable(session.getExternalKey()).orElse(new ExternalSessionKey("No external key was assigned"));
        new Thread(() -> _release(session.getSlot(), reason), "Terminate Test Session int id: ["
                + internalKey + "] ext id: [" + externalKey + "] container: [" + remoteName + "]").start();
    }

    /**
     * Release the test slot. Free the resource on the slot itself and the registry. If also invokes
     * the {@link org.openqa.grid.internal.listeners.TestSessionListener#afterSession(TestSession)} if
     * applicable.
     *
     * @param testSlot The slot to release
     */
    private void _release(TestSlot testSlot, SessionTerminationReason reason) {
        if (!testSlot.startReleaseProcess()) {
            return;
        }

        if (!testSlot.performAfterSessionEvent()) {
            return;
        }

        final String internalKey = testSlot.getInternalKey();

        try {
            lock.lock();
            testSlot.finishReleaseProcess();
            release(internalKey, reason);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @see GridRegistry#removeIfPresent(RemoteProxy)
     */
    public void removeIfPresent(RemoteProxy proxy) {
        // Find the original proxy. While the supplied one is logically equivalent, it may be a fresh object with
        // an empty TestSlot list, which doesn't figure into the proxy equivalence check.  Since we want to free up
        // those test sessions, we need to operate on that original object.
        if (proxies.contains(proxy)) {
            LOG.warn(String.format(
                    "Cleaning up stale test sessions on the unregistered node %s", proxy));

            final RemoteProxy p = proxies.remove(proxy);
            p.getTestSlots().forEach(testSlot -> forceRelease(testSlot, SessionTerminationReason.PROXY_REREGISTRATION) );
            p.teardown();
        }
    }

    /**
     * @see GridRegistry#forceRelease(TestSlot, SessionTerminationReason)
     */
    public void forceRelease(TestSlot testSlot, SessionTerminationReason reason) {
        if (testSlot.getSession() == null) {
            return;
        }

        String internalKey = testSlot.getInternalKey();
        release(internalKey, reason);
        testSlot.doFinishRelease();
    }

    /**
     * @see GridRegistry#stop()
     */
    public void stop() {
        stop = true;
        matcherThread.interrupt();
        newSessionQueue.stop();
        proxies.teardown();
    }

    /**
     * @see GridRegistry#addNewSessionRequest(RequestHandler)
     */
    public void addNewSessionRequest(RequestHandler handler) {
        try {
            lock.lock();
            Map<String, Object> requestedCapabilities = handler.getRequest().getDesiredCapabilities();
            proxies.verifyAbilityToHandleDesiredCapabilities(requestedCapabilities);
            requestedCapabilities.forEach((k, v) -> MDC.put(k,v.toString()));
            LOG.info("Adding sessionRequest for " + requestedCapabilities.toString());
            newSessionQueue.add(handler);
            seleniumTestSessionsWaiting.inc();
            fireMatcherStateChanged();
        } finally {
            MDC.clear();
            lock.unlock();
        }
    }

    /**
     * iterates the list of incoming session request to find a potential match in the list of proxies.
     * If something changes in the registry, the matcher iteration is stopped to account for that
     * change.
     */
    private void assignRequestToProxy() {
        while (!stop) {
            try {
                testSessionAvailable.await(5, TimeUnit.SECONDS);
                newSessionQueue.processQueue(this::takeRequestHandler, getHub().getConfiguration().prioritizer);
                // Just make sure we delete anything that is logged on this thread from memory
                LoggingManager.perSessionLogHandler().clearThreadTempLogs();
            } catch (InterruptedException e) {
                LOG.info("Shutting down registry.");
            } catch (Throwable t) {
                LOG.error("Unhandled exception in Matcher thread.", t);
            }
        }

    }

    private boolean takeRequestHandler(RequestHandler handler) {
        final TestSession session = proxies.getNewSession(handler.getRequest().getDesiredCapabilities());
        final boolean sessionCreated = session != null;
        if (sessionCreated) {
            String remoteName = session.getSlot().getProxy().getId();
            long timeToAssignProxy = System.currentTimeMillis() - handler.getRequest().getCreationTime();
            LOG.info(String.format("Test session with internal key %s assigned to remote (%s) after %s seconds (%s ms).",
                                  session.getInternalKey(),
                                  remoteName,
                                  timeToAssignProxy / 1000,
                                  timeToAssignProxy));
            seleniumTestSessionStartLatency.observe(timeToAssignProxy / Collector.MILLISECONDS_PER_SECOND);
            seleniumTestSessionsWaiting.dec();
            activeTestSessions.add(session);
            handler.bindSession(session);
        }
        return sessionCreated;
    }

    /**
     * mark the session as finished for the registry. The resources that were associated to it are now
     * free to be reserved by other tests
     *
     * @param session The session
     * @param reason  the reason for the release
     */
    private void release(TestSession session, SessionTerminationReason reason) {
        try {
            lock.lock();
            boolean removed = activeTestSessions.remove(session, reason);
            if (removed) {
                fireMatcherStateChanged();
            }
        } finally {
            lock.unlock();
        }
    }

    private void release(String internalKey, SessionTerminationReason reason) {
        if (internalKey == null) {
            return;
        }
        final TestSession session = activeTestSessions.findSessionByInternalKey(internalKey);
        if (session != null) {
            release(session, reason);
            return;
        }
        LOG.warn("Tried to release session with internal key " + internalKey +
                " but couldn't find it.");
    }

    /**
     * @see GridRegistry#add(RemoteProxy)
     */
    public void add(RemoteProxy proxy) {
        if (proxy == null) {
            return;
        }

    	LOG.info("Registered a node " + proxy);

        try {
            lock.lock();

            removeIfPresent(proxy);

            if (registeringProxies.contains(proxy)) {
                LOG.warn(String.format("Proxy '%s' is already queued for registration.", proxy));

                return;
            }

            registeringProxies.add(proxy);
            fireMatcherStateChanged();
        } finally {
            lock.unlock();
        }

        boolean listenerOk = true;
        try {
            if (proxy instanceof RegistrationListener) {
                ((RegistrationListener) proxy).beforeRegistration();
            }
        } catch (Throwable t) {
            LOG.error("Error running the registration listener on " + proxy + ", " + t.getMessage());
            t.printStackTrace();
            listenerOk = false;
        }

        try {
            lock.lock();
            registeringProxies.remove(proxy);
            if (listenerOk) {
                if (proxy instanceof SelfHealingProxy) {
                    ((SelfHealingProxy) proxy).startPolling();
                }
                proxies.add(proxy);
                fireMatcherStateChanged();
            }
        } finally {
            lock.unlock();
        }

    }

    /**
     * @see GridRegistry#setThrowOnCapabilityNotPresent(boolean)
     */
    public void setThrowOnCapabilityNotPresent(boolean throwOnCapabilityNotPresent) {
        proxies.setThrowOnCapabilityNotPresent(throwOnCapabilityNotPresent);
    }

    private void fireMatcherStateChanged() {
        testSessionAvailable.signalAll();
    }

    /**
     * @see GridRegistry#getAllProxies()
     */
    public ProxySet getAllProxies() {
        return proxies;
    }

    /**
     * @see GridRegistry#getUsedProxies()
     */
    public List<RemoteProxy> getUsedProxies() {
        return proxies.getBusyProxies();
    }

    /**
     * @see GridRegistry#getSession(ExternalSessionKey)
     */
    public TestSession getSession(ExternalSessionKey externalKey) {
        return activeTestSessions.findSessionByExternalKey(externalKey);
    }

    /**
     * @see GridRegistry#getExistingSession(ExternalSessionKey)
     */
    public TestSession getExistingSession(ExternalSessionKey externalKey) {
        return activeTestSessions.getExistingSession(externalKey);
    }

    /**
     * @see GridRegistry#getNewSessionRequestCount()
     */
    public int getNewSessionRequestCount() {
        // may race
        return newSessionQueue.getNewSessionRequestCount();
    }

    /**
     * @see GridRegistry#clearNewSessionRequests()
     */
    public void clearNewSessionRequests() {
        newSessionQueue.clearNewSessionRequests();
        seleniumTestSessionsWaiting.set(0);
    }

    /**
     * @see GridRegistry#removeNewSessionRequest(RequestHandler)
     */
    public boolean removeNewSessionRequest(RequestHandler request) {
        boolean wasRemoved = newSessionQueue.removeNewSessionRequest(request);
        if (wasRemoved) {
            seleniumTestSessionsWaiting.dec();
        }
        return wasRemoved;
    }

    /**
     * @see GridRegistry#getDesiredCapabilities()
     */
    public Iterable<DesiredCapabilities> getDesiredCapabilities() {
        return newSessionQueue.getDesiredCapabilities();
    }

    /**
     * @see GridRegistry#getActiveSessions()
     */
    public Set<TestSession> getActiveSessions() {
        return activeTestSessions.unmodifiableSet();
    }

    /**
     * @see GridRegistry#getProxyById(String)
     */
    public RemoteProxy getProxyById(String id) {
        LOG.debug("Getting proxy " + id);
        return proxies.getProxyById(id);
    }

    protected static class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e) {
            LOG.debug("Matcher thread dying due to unhandled exception.", e);
        }
    }

    @Override
    public HttpClient getHttpClient(URL url, int connectionTimeout, int readTimeout) {
        // https://github.com/zalando/zalenium/issues/491
        int maxTries = 3;
        for (int i = 1; i <= maxTries; i++) {
            try {
                HttpClient client = httpClientFactory.createClient(url);
                if (i > 1) {
                    String message = String.format("Successfully created HttpClient for url %s, after attempt #%s", url, i);
                    LOG.warn(message);
                }
                return client;
            } catch (Exception | AssertionError e) {
                String message = String.format("Error while getting the HttpClient for url %s, attempt #%s", url, i);
                LOG.debug(message, e);
                if (i == maxTries) {
                    throw e;
                }
                try {
                    Thread.sleep((new Random().nextInt(5) + 1) * 1000);
                } catch (InterruptedException exception) {
                    LOG.error("Something went wrong while delaying the HttpClient creation after a failed attempt", exception);
                }
            }
        }
        throw new IllegalStateException(String.format("Something went wrong while creating a HttpClient for url %s", url));
    }

    /**
     * iterates the queue of incoming new session request and assign them to proxy after they've been
     * sorted by priority, with priority defined by the prioritizer.
     */
    class Matcher extends Thread { // Thread safety reviewed

        Matcher() {
            super("Matcher thread");
        }

        @Override
        public void run() {
            try {
                lock.lock();
                assignRequestToProxy();
            } finally {
                lock.unlock();
            }
        }

    }

}

package de.zalando.ep.zalenium.proxy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.vandermeer.asciitable.AT_Row;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import io.prometheus.client.Gauge;
import net.jcip.annotations.ThreadSafe;

/**
 * Automatically starts remote proxies in response to demand for test sessions.
 * 
 * On startup, will start a configurable minimum number of proxies, and will
 * attempt to maintain that minimum.
 * 
 * Monitors the state of containers to automatically remove proxies from the set
 * as the containers are shutdown.
 * 
 * Generally, the lifecycle of a container is:
 * <ol>
 * <li>a new proxy is requested. The container is started and added to the
 * startedContainers map.</li>
 * <li>the container starts and that fact is recorded in the map.</li>
 * <li>the proxy in the container registers with the grid, and the proxy is
 * added to the set and recorded in the map (this may happen before step 2.).
 * </li>
 * <li>the proxy is no longer needed so it is marked unavailable and a request
 * is sent to stop the container.</li>
 * <li>the proxy deregisters - another attempt will be made to stop the
 * container and it will be removed from the set.</li>
 * <li>the container stops and is removed from the map.</li>
 * </ol>
 * 
 * In some cases, a stopping container's proxy may re-register with the grid so
 * care is taken to ensure that:
 * <ul>
 * <li>the proxy will not be allocated a test (it will fail when the container
 * actually stops)</li>
 * <li>proxies are always tracked and shutdown when appropriate.
 * <li>
 * </ul>
 */
@ThreadSafe
public class AutoStartProxySet extends ProxySet implements Iterable<RemoteProxy> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoStartProxySet.class.getName());
    
    private static final Logger STATUS_LOGGER = LoggerFactory.getLogger(LOGGER.getName() + ".Status");

    private final Map<ContainerCreationStatus, ContainerStatus> startedContainers = new ConcurrentHashMap<>();

    private final DockeredSeleniumStarter starter;

    private final SessionRequestFilter filter = new SessionRequestFilter();

    private final long minContainers;
    private final long maxContainers;
    private final long timeToWaitToStart;
    private final boolean waitForAvailableNodes;

    private final Thread poller;

    private long timeOfLastReport = 0;

    private Clock clock;
    
    static final Gauge seleniumContainersStarting = Gauge.build()
            .name("selenium_containers_starting").help("The number of Selenium Containers that are starting.").register();
    
    static final Gauge seleniumContainersRunning = Gauge.build()
            .name("selenium_containers_running").help("The number of Selenium Containers that are running.").register();
    
    public AutoStartProxySet(boolean throwOnCapabilityNotPresent, long minContainers, long maxContainers,
            long timeToWaitToStart, boolean waitForAvailableNodes, DockeredSeleniumStarter starter, Clock clock) {
        super(throwOnCapabilityNotPresent);
        this.minContainers = minContainers;
        this.maxContainers = maxContainers;
        this.timeToWaitToStart = timeToWaitToStart;
        this.waitForAvailableNodes = waitForAvailableNodes;
        this.starter = starter;
        this.clock = clock;

        poller = new Thread(() -> {
            LOGGER.info("Starting poller.");
            while (true) {

                long now = clock.millis();
                if (now - timeOfLastReport > 30000) {
                    dumpStatus();
                    timeOfLastReport = now;
                }

                LOGGER.debug("Checking containers...");
                try {
                    checkContainers();
                } catch (Exception e) {
                    LOGGER.error("Failed checking containers.", e);
                }
                LOGGER.debug("Checked containers.");

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    LOGGER.info("Stopping polling thread.");
                    LOGGER.debug("Stopping polling thread.", e);
                }
            }
        });

        poller.setName("AutoStartProxyPoolPoller");

        poller.start();
    }

    @Override
    public void teardown() {
        poller.interrupt();
        super.teardown();
    }

    /**
     * Creates a new session (if possible) on a proxy.
     * 
     * If no session can be created, returns null and requests the creation of a new
     * proxy.
     */
    public TestSession getNewSession(Map<String, Object> desiredCapabilities) {
        int busy = super.getBusyProxies().size();
        int total = super.size();

        List<RemoteProxy> proxies = super.getSorted();
        proxies.stream().forEach(proxy -> {
            String id = proxy.getId();
            boolean hasCapability = proxy.hasCapability(desiredCapabilities);
            LOGGER.debug(String.format("[%s] [%s] has capability ? [%s].", id, proxy.getClass(), hasCapability));
        });

        LOGGER.debug(String.format("[%d] of [%d] proxies are busy.", busy, total));

        TestSession newSession = super.getNewSession(desiredCapabilities);
        if (newSession == null) {
            this.start(desiredCapabilities);
        }
        else {
            filter.testSessionHasStarted(desiredCapabilities);
        }
        return newSession;
    }

    public void add(RemoteProxy proxy) {
        boolean shouldAdd = true;
        if (proxy instanceof DockerSeleniumRemoteProxy) {
            DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;
            shouldAdd = this.register(dockerSeleniumRemoteProxy);
        }

        if (shouldAdd) {
            super.add(proxy);
        }
        else {
            // Won't be tracking the proxy, so it won't be removed and shutdown later - tear down.
            proxy.teardown();
        }
    }

    public RemoteProxy remove(RemoteProxy proxy) {
        if (proxy instanceof DockerSeleniumRemoteProxy) {
            DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;
            // Always try to remove the proxy from the pool - this will stop the container.
            String containerId = dockerSeleniumRemoteProxy.getContainerId();

            try {
                LOGGER.info("Stopping removed container [" + containerId + "].");
                starter.stopContainer(containerId);
                seleniumContainersRunning.dec();
            } catch (Exception e) {
                LOGGER.error("Failed to stop container [" + containerId + "].", e);
            }
        }
        return super.remove(proxy);
    }

    /**
     * If possible, starts a new proxy that can satisfy the requested capabilities.
     * 
     * If too many proxies are already running, then a new proxy will not be
     * started.
     * 
     * If the request has been made previously, then a new proxy will not be
     * started.
     * 
     * If a proxy that would otherwise be able to service the request is currently
     * cleaning up and will be available shortly, then a new proxy will not be
     * started.
     * 
     * @param desiredCapabilities
     *            capabilities of the proxy to be started.
     */
    public synchronized void start(Map<String, Object> desiredCapabilities) {

        if (startedContainers.size() >= this.maxContainers) {
            LOGGER.info("Not starting new container, there are [{}] of max [{}] created.", startedContainers.size(),
                    this.maxContainers);
            return;
        }

        if (nodesAvailable(desiredCapabilities)) {
            LOGGER.debug(
                    String.format("A node is coming up soon for %s, won't start a new node yet.", desiredCapabilities));
            return;
        }

        if (filter.hasRequestBeenProcessed(desiredCapabilities)) {
            LOGGER.debug("Request {}, has been processed and it is waiting for a node.", desiredCapabilities);
            return;
        }

        LOGGER.info("No proxy available for new session, starting new.");

        this.startContainer(desiredCapabilities);

        filter.cleanProcessedCapabilities();
    }

    private boolean register(DockerSeleniumRemoteProxy proxy) {
        String containerId = proxy.getContainerId();

        ContainerStatus containerStatus = null;

        synchronized (this) {
            for (Entry<ContainerCreationStatus, ContainerStatus> container : this.startedContainers.entrySet()) {
                if (Objects.equals(container.getKey().getContainerName(), containerId)
                        || Objects.equals(container.getKey().getContainerId(), containerId)) {
                    container.getValue().setProxy(Optional.of(proxy));
                    containerStatus = container.getValue();
                    break;
                }
            }
        }

        if (containerStatus == null) {
            LOGGER.warn(
                    "Registered (or re-registered) a container {} {} that is not tracked by the pool, marking down.",
                    containerId, proxy);
            proxy.markDown();
            return false;
        } else if (containerStatus.isShuttingDown()) {
            LOGGER.warn("Registered (or re-registered) a container {} {} that is shutting down, marking down.",
                    containerId, proxy);
            proxy.markDown();
            return false;
        } else {
            LOGGER.info("Registered a container {} {}.", containerId, proxy);
            seleniumContainersStarting.dec();
            seleniumContainersRunning.inc();
            return true;
        }
    }

    /**
     * Checks the status of the containers.
     */
    public void checkContainers() {
        LOGGER.debug("Checking {} containers.", startedContainers.size());
        synchronized (this) {
            if (startedContainers.size() < this.minContainers) {
                LOGGER.info("Autostarting container, because {} is less than min {}", startedContainers.size(),
                        this.minContainers);
                long outstanding = this.minContainers - startedContainers.size();
                for (int i = 0; i < outstanding; i++) {
                    this.startContainer(Collections.emptyMap());
                }
            }
        }

        Set<ContainerCreationStatus> idleProxies = new HashSet<>();

        Set<ContainerCreationStatus> deadProxies = this.startedContainers.keySet().stream()
                .filter(starter::containerHasFinished).collect(Collectors.toSet());

        for (ContainerCreationStatus containerCreationStatus : deadProxies) {
            LOGGER.info("Container {} is terminated. Removing from tracked set.", containerCreationStatus);
            ContainerStatus removedProxy = this.startedContainers.remove(containerCreationStatus);
            Optional.ofNullable(removedProxy).flatMap(ContainerStatus::getProxy).ifPresent(proxy -> {
                proxy.markDown();
                if (contains(proxy)) {
                    super.remove(proxy);
                }
            });
        }

        for (Entry<ContainerCreationStatus, ContainerStatus> container : this.startedContainers.entrySet()) {
            ContainerCreationStatus creationStatus = container.getKey();
            ContainerStatus containerStatus = container.getValue();

            // Only need to check containers that haven't yet started.
            if (!containerStatus.isStarted()) {
                if (starter.containerHasStarted(creationStatus)) {
                    long started = clock.millis();
                    containerStatus.setTimeStarted(Optional.of(started));
                    LOGGER.info("Container {} started after {}.", creationStatus.getContainerName(),
                            (started - containerStatus.getTimeCreated()));
                } else {
                    long timeWaitingToStart = clock.millis() - containerStatus.getTimeCreated();
                    if (timeWaitingToStart > this.timeToWaitToStart) {
                        LOGGER.warn("Waited {} for {} to start, which is longer than {}.", timeWaitingToStart,
                                containerStatus, this.timeToWaitToStart);
                    }
                }
            }
        }

        this.startedContainers.entrySet().stream().filter(entry -> !entry.getValue().isShuttingDown()). // Do not count
                                                                                                        // already
                                                                                                        // terminating
                                                                                                        // proxies.
                flatMap(entry -> entry.getValue().getProxy().filter(DockerSeleniumRemoteProxy::shutdownIfStale)
                        .map(proxy -> Stream.of(Pair.of(entry, proxy))).orElse(Stream.empty()))
                .forEach(pair -> {
                    idleProxies.add(pair.getLeft().getKey());
                    pair.getLeft().getValue().setShuttingDown(true);
                });

        long runningCount = this.startedContainers.values().stream().filter(container -> !container.isShuttingDown())
                .count();

        if (runningCount > this.minContainers) {
            LOGGER.debug("Timing out containers because active container count {} is greater than min {}.",
                    runningCount, this.minContainers);
            long extra = runningCount - minContainers;

            this.startedContainers.entrySet().stream().filter(entry -> !entry.getValue().isShuttingDown()). // Do not
                                                                                                            // count
                                                                                                            // already
                                                                                                            // terminating
                                                                                                            // proxies.
                    flatMap(entry -> entry.getValue().getProxy().filter(DockerSeleniumRemoteProxy::shutdownIfIdle)
                            .map(proxy -> Stream.of(Pair.of(entry, proxy))).orElse(Stream.empty()))
                    .limit(extra).forEach(pair -> {
                        idleProxies.add(pair.getLeft().getKey());
                        pair.getLeft().getValue().setShuttingDown(true);
                    });
        }

        LOGGER.debug(String.format("[%d] proxies are idle and will be removed.", idleProxies.size()));
    }

    /**
     * Starts a container. Records that a request has been processed so that a
     * retried request will not cause extra proxies to be started.
     */
    private synchronized void startContainer(Map<String, Object> desiredCapabilities) {
        // Synchronized so that we record the container creation status before register
        // can be called
        ContainerCreationStatus startedContainer = starter.startDockerSeleniumContainer(desiredCapabilities);
        if (startedContainer == null) {
            LOGGER.info(String.format("Failed to start container."));
        } else {
            filter.requestHasBeenProcesssed(desiredCapabilities);
            startedContainers.put(startedContainer,
                    new ContainerStatus(startedContainer.getContainerName(), clock.millis()));
            LOGGER.info("Created {}.", startedContainer);
            seleniumContainersStarting.inc();
        }
    }

    private boolean nodesAvailable(Map<String, Object> requestedCapability) {
        if (!waitForAvailableNodes) {
            LOGGER.debug(String.format("Not waiting for available slots, creating nodes when possible."));
            return false;
        }

        // If a node is cleaning up it will be available soon
        // It is faster and more resource wise to wait for the node to be ready

        boolean available = this.startedContainers.values().stream().anyMatch(status -> {
            return status.getProxy()
                    .filter(p -> p.isCleaningUpBeforeNextSession() && p.hasCapability(requestedCapability)).isPresent();
        });

        if (available) {
            LOGGER.debug(String.format("A node is coming up to handle this request."));
            return true;
        }

        LOGGER.debug("No slots available, a new node will be created.");
        return false;
    }

    private void dumpStatus() {
        if (STATUS_LOGGER.isDebugEnabled()) {
            final AsciiTable at = new AsciiTable();

            at.addRule();

            AT_Row headerRow = at.addRow("Id", "Proxy", "Created", "Started", "Last Used", "Last Session", "Busy",
                    "Timed Out", "Terminating", "Tests Run");
            headerRow.getCells().get(6).getContext().setTextAlignment(TextAlignment.RIGHT);
            headerRow.getCells().get(7).getContext().setTextAlignment(TextAlignment.RIGHT);
            headerRow.getCells().get(8).getContext().setTextAlignment(TextAlignment.RIGHT);
            headerRow.getCells().get(9).getContext().setTextAlignment(TextAlignment.RIGHT);

            at.addRule();

            this.startedContainers.forEach((creationStatus, containerStatus) -> {
                final String proxyId = containerStatus.getProxy().map(p -> p.getId()).orElse("-");
                final String containerId = creationStatus.getContainerName();
                final String timeCreated = dateTime(containerStatus.getTimeCreated());
                final String timeStarted = containerStatus.getTimeStarted().map(AutoStartProxySet::dateTime)
                        .orElse("-");
                final String lastUsed = containerStatus.getProxy().map(DockerSeleniumRemoteProxy::getLastCommandTime)
                        .map(AutoStartProxySet::dateTime).orElse("-");
                final String lastSession = containerStatus.getProxy()
                        .map(DockerSeleniumRemoteProxy::getLastSessionStart).map(AutoStartProxySet::dateTime)
                        .orElse("-");
                final Boolean isBusy = containerStatus.getProxy().map(DockerSeleniumRemoteProxy::isBusy).orElse(false);
                final Boolean isTimedOut = containerStatus.getProxy().map(DockerSeleniumRemoteProxy::isTimedOut)
                        .orElse(false);
                final Boolean isShuttingDown = containerStatus.isShuttingDown();
                final int testCount = containerStatus.getProxy()
                        .map(DockerSeleniumRemoteProxy::getAmountOfExecutedTests).orElse(0);

                AT_Row row = at.addRow(containerId, proxyId, timeCreated, timeStarted, lastUsed, lastSession, isBusy,
                        isTimedOut, isShuttingDown, testCount);
                row.getCells().get(6).getContext().setTextAlignment(TextAlignment.RIGHT);
                row.getCells().get(7).getContext().setTextAlignment(TextAlignment.RIGHT);
                row.getCells().get(8).getContext().setTextAlignment(TextAlignment.RIGHT);
                row.getCells().get(9).getContext().setTextAlignment(TextAlignment.RIGHT);
            });

            at.addRule();
            at.setPaddingLeftRight(1);
            CWC_LongestLine cwc = new CWC_LongestLine();
            at.getRenderer().setCWC(cwc);

            STATUS_LOGGER.debug("Status:\n" + at.render(200));
        }
    }

    private static String dateTime(long epochMillis) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        LocalDateTime date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
        return formatter.format(date);
    }

    private static final class ContainerStatus {
        private final String containerId;
        private final long timeCreated;
        private Optional<Long> timeStarted = Optional.empty();
        private Optional<DockerSeleniumRemoteProxy> proxy = Optional.empty();
        private boolean shuttingDown = false;

        public ContainerStatus(String containerId, long timeCreated) {
            super();
            this.containerId = containerId;
            this.timeCreated = timeCreated;
        }

        public Optional<Long> getTimeStarted() {
            return timeStarted;
        }

        public void setTimeStarted(Optional<Long> timeStarted) {
            this.timeStarted = timeStarted;
        }

        @SuppressWarnings("unused")
        public String getContainerId() {
            return containerId;
        }

        public long getTimeCreated() {
            return timeCreated;
        }

        public boolean isStarted() {
            return timeStarted.isPresent();
        }

        public Optional<DockerSeleniumRemoteProxy> getProxy() {
            return proxy;
        }

        public void setProxy(Optional<DockerSeleniumRemoteProxy> proxy) {
            this.proxy = proxy;
        }

        public boolean isShuttingDown() {
            return shuttingDown;
        }

        public void setShuttingDown(boolean shuttingDown) {
            this.shuttingDown = shuttingDown;
        }

        @Override
        public String toString() {
            return "ContainerStatus [containerId=" + containerId + ", timeCreated=" + timeCreated + ", timeStarted="
                    + timeStarted + ", proxy=" + proxy + ", shuttingDown=" + shuttingDown + "]";
        }

    }

}

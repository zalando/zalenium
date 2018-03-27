
// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

package de.zalando.ep.zalenium.proxy;

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
import net.jcip.annotations.ThreadSafe;

/**
 * A set of RemoteProxies.
 *
 * Obeys the iteration guarantees of CopyOnWriteArraySet
 */
@ThreadSafe
public class AutoStartProxySet extends ProxySet implements Iterable<RemoteProxy> {

	private static final Logger LOGGER = LoggerFactory.getLogger(AutoStartProxySet.class.getName());

	private final Pool pool = new Pool(DockerSeleniumStarterRemoteProxy.getDesiredContainersOnStartup(),
			DockerSeleniumStarterRemoteProxy.getMaxDockerSeleniumContainers(), 180000, true, this);

	public AutoStartProxySet(boolean throwOnCapabilityNotPresent) {
		super(throwOnCapabilityNotPresent);
	}

	@Override
	public void teardown() {
		pool.teardown();
		super.teardown();
	}

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
			pool.start(desiredCapabilities);
		}
		return newSession;
	}

	public void add(RemoteProxy proxy) {
		if (proxy instanceof DockerSeleniumRemoteProxy) {
			DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;
			pool.register(dockerSeleniumRemoteProxy);
		}
		super.add(proxy);
	}

	public RemoteProxy remove(RemoteProxy proxy) {
		if (proxy instanceof DockerSeleniumRemoteProxy) {
			DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = (DockerSeleniumRemoteProxy) proxy;
			// Always try to remove the proxy from the pool - this will stop the container.
			pool.remove(dockerSeleniumRemoteProxy);
		}
		return super.remove(proxy);
	}

	private static final class Pool {
		private final Map<ContainerCreationStatus, ContainerStatus> startedContainers = new ConcurrentHashMap<>();
		
		private final long minContainers;
		private final long maxContainers;
		private final long timeToWaitToStart;
		private final boolean waitForAvailableNodes;

		private final DockeredSeleniumStarter starter = new DockeredSeleniumStarter();

		private final SessionRequestFilter filter = new SessionRequestFilter();
		
		private final Thread poller;
		
		private long timeOfLastReport = 0;
		private ProxySet proxySet;

		public Pool(long minContainers, long maxContainers, long timeToWaitToStart, boolean waitForAvailableNodes, ProxySet proxySet) {
			super();
			this.minContainers = minContainers;
			this.maxContainers = maxContainers;
			this.timeToWaitToStart = timeToWaitToStart;
			this.waitForAvailableNodes = waitForAvailableNodes;
			this.proxySet = proxySet;

			poller = new Thread(new Runnable() {
				@Override
				public void run() {
					LOGGER.info("Starting poller.");
					while (true) {
						
						long now = System.currentTimeMillis();
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
							e.printStackTrace();
						}
					}
				}
			});

			poller.setName("AutoStartProxyPoolPoller");

			poller.start();
		}

		public void register(DockerSeleniumRemoteProxy proxy) {
			String containerId = proxy.getContainerId();

			ContainerStatus containerStatus = null;
			
			synchronized (this) {
				for (Entry<ContainerCreationStatus, ContainerStatus> container : this.startedContainers.entrySet()) {
					if (Objects.equals(container.getKey().getContainerName(), containerId)) {
						container.getValue().setProxy(Optional.of(proxy));
						containerStatus = container.getValue();
						break;
					}
				}
			}
			
			if (containerStatus == null) {
				LOGGER.warn("Registered (or re-registered) a container {} {} that is not tracked by the pool, marking down.", containerId, proxy);
				proxy.markDown();
			}
			else if (containerStatus.isShuttingDown()) {
				LOGGER.warn("Registered (or re-registered) a container {} {} that is shutting down, marking down.", containerId, proxy);
				proxy.markDown();
			}
			else {
				LOGGER.info("Registered a container {} {}.", containerId, proxy);
			}
		}

		public synchronized void start(Map<String, Object> desiredCapabilities) {

			if (startedContainers.size() >= this.maxContainers) {
				LOGGER.info("Not starting new container, there are [{}] of max [{}] created.", startedContainers.size(),
						this.maxContainers);
				return;
			}

			if (nodesAvailable(desiredCapabilities)) {
				LOGGER.debug(String.format("A node is coming up soon for %s, won't start a new node yet.",
						desiredCapabilities));
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

		public synchronized void teardown() {
			poller.interrupt();
		}

	    // Synchronize so that we record the container creation status before register can be called
		private synchronized void startContainer(Map<String, Object> desiredCapabilities) {
			ContainerCreationStatus startedContainer = starter.startDockerSeleniumContainer(desiredCapabilities);
			if (startedContainer == null) {
				LOGGER.info(String.format("Failed to start container."));
			} else {
				filter.requestHasBeenProcesssed(desiredCapabilities);
				startedContainers.put(startedContainer,
						new ContainerStatus(startedContainer.getContainerName(), System.currentTimeMillis()));
				LOGGER.info("Created {}.", startedContainer);
			}
		}

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
			
			Set<ContainerCreationStatus> deadProxies = 
					this.startedContainers.keySet().stream().filter(starter::containerHasFinished).collect(Collectors.toSet());
			
			for (ContainerCreationStatus containerCreationStatus : deadProxies) {
				LOGGER.info("Container {} is terminated. Removing from tracked set.", containerCreationStatus);
				ContainerStatus removedProxy = this.startedContainers.remove(containerCreationStatus);
				Optional.ofNullable(removedProxy).flatMap(ContainerStatus::getProxy).ifPresent(DockerSeleniumRemoteProxy::markDown);
			}

			for (Entry<ContainerCreationStatus, ContainerStatus> container : this.startedContainers.entrySet()) {
				ContainerCreationStatus creationStatus = container.getKey();
				ContainerStatus containerStatus = container.getValue();

				if (containerStatus.isStarted()) {
					// No need to check.
				} else {
					if (starter.containerHasStarted(creationStatus)) {
						long started = System.currentTimeMillis();
						containerStatus.setTimeStarted(Optional.of(started));
						LOGGER.info("Container {} started after {}.", creationStatus.getContainerName(),
								(started - containerStatus.getTimeCreated()));
					} else {
						long timeWaitingToStart = System.currentTimeMillis() - containerStatus.getTimeCreated();
						if (timeWaitingToStart > this.timeToWaitToStart) {
							LOGGER.warn("Waited {} for {} to start, which is longer than {}.", timeWaitingToStart,
									containerStatus, this.timeToWaitToStart);
						}
					}
				}
			}
			
			this.startedContainers.entrySet().
				stream().
				filter(entry -> !entry.getValue().isShuttingDown()). // Do not count already terminating proxies.
				flatMap(entry -> 
					entry.getValue().getProxy().filter(DockerSeleniumRemoteProxy::shutdownIfStale).map(proxy -> Stream.of(Pair.of(entry, proxy))).orElse(Stream.empty())).
				forEach(pair -> {
					idleProxies.add(pair.getLeft().getKey());
					pair.getLeft().getValue().setShuttingDown(true);
				});
			
			long runningCount = this.startedContainers.values().stream().filter(container -> !container.isShuttingDown()).count();
			
			if (runningCount > this.minContainers) {
				LOGGER.debug("Timing out containers because active container count {} is greater than min {}.", runningCount, this.minContainers);
				long extra = runningCount - minContainers;
				
					this.startedContainers.entrySet().
						stream().
						filter(entry -> !entry.getValue().isShuttingDown()). // Do not count already terminating proxies.
						flatMap(entry -> 
							entry.getValue().getProxy().filter(DockerSeleniumRemoteProxy::shutdownIfIdle).map(proxy -> Stream.of(Pair.of(entry, proxy))).orElse(Stream.empty())).
						limit(extra).
						forEach(pair -> {
							idleProxies.add(pair.getLeft().getKey());
							pair.getLeft().getValue().setShuttingDown(true);
						});
			}
			
			
			
			
			LOGGER.debug(String.format("[%d] proxies are idle and will be removed.", idleProxies.size()));
		}

		public void remove(DockerSeleniumRemoteProxy proxy) {
			String containerId = proxy.getContainerId();
			
			try {
				starter.stopContainer(containerId);
			} catch (Exception e) {	
				LOGGER.error("Failed to stop container [" + containerId + "].", e);
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
						.filter(p -> p.isCleaningUpBeforeNextSession() && p.hasCapability(requestedCapability))
						.isPresent();
			});

			if (available) {
				LOGGER.debug(String.format("A node is coming up to handle this request."));
				return true;
			}

			LOGGER.debug("No slots available, a new node will be created.");
			return false;
		}

		private static String dateTime(long epochMillis) {
			DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
			LocalDateTime date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
			return formatter.format(date);
		}
		
		private void dumpStatus() {
			
			final AsciiTable at = new AsciiTable();
			
			at.addRule();
			
			AT_Row headerRow = at.addRow("Id", "Proxy", "Created", "Started", "Last Used", "Last Session", "Busy", "Timed Out", "Terminating", "Tests Run");
			headerRow.getCells().get(6).getContext().setTextAlignment(TextAlignment.RIGHT);
			headerRow.getCells().get(7).getContext().setTextAlignment(TextAlignment.RIGHT);
			headerRow.getCells().get(8).getContext().setTextAlignment(TextAlignment.RIGHT);
			headerRow.getCells().get(9).getContext().setTextAlignment(TextAlignment.RIGHT);
			
			at.addRule();
			
			this.startedContainers.forEach((creationStatus, containerStatus) -> {
				final String proxyId = containerStatus.getProxy().map(p -> p.getId()).orElse("-");
				final String containerId = creationStatus.getContainerName();
				final String timeCreated = dateTime(containerStatus.getTimeCreated());
				final String timeStarted = containerStatus.getTimeStarted().map(Pool::dateTime).orElse("-");
				final String lastUsed = containerStatus.getProxy().map(DockerSeleniumRemoteProxy::getLastCommandTime).map(Pool::dateTime).orElse("-");
				final String lastSession = containerStatus.getProxy().map(DockerSeleniumRemoteProxy::getLastSessionStart).map(Pool::dateTime).orElse("-");
				final Boolean isBusy = containerStatus.getProxy().map(DockerSeleniumRemoteProxy::isBusy).orElse(false);
				final Boolean isTimedOut = containerStatus.getProxy().map(DockerSeleniumRemoteProxy::isTimedOut).orElse(false);
				final Boolean isShuttingDown = containerStatus.isShuttingDown();
				final int testCount = containerStatus.getProxy().map(DockerSeleniumRemoteProxy::getAmountOfExecutedTests).orElse(0);
				
				AT_Row row = at.addRow(containerId, proxyId, timeCreated, timeStarted, lastUsed, lastSession, isBusy, isTimedOut, isShuttingDown, testCount);
				row.getCells().get(6).getContext().setTextAlignment(TextAlignment.RIGHT);
				row.getCells().get(7).getContext().setTextAlignment(TextAlignment.RIGHT);
				row.getCells().get(8).getContext().setTextAlignment(TextAlignment.RIGHT);
				row.getCells().get(9).getContext().setTextAlignment(TextAlignment.RIGHT);
			});
			
			at.addRule();
			at.setPaddingLeftRight(1);
			CWC_LongestLine cwc = new CWC_LongestLine();
			at.getRenderer().setCWC(cwc);
			
			System.out.println(at.render(200));
		}

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

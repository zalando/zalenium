package de.zalando.tip.zalenium.proxy;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.exception.RemoteNotReachableException;
import org.openqa.grid.common.exception.RemoteUnregisterException;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.selenium.proxy.DefaultRemoteProxy;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
    The implementation of this class was inspired on https://gist.github.com/krmahadevan/4649607
 */
public class DockerSeleniumRemoteProxy extends DefaultRemoteProxy {

    private static final Logger LOGGER = Logger.getLogger(DockerSeleniumRemoteProxy.class.getName());

    // Amount of tests that can be executed in the node
    private final static int MAX_UNIQUE_TEST_SESSIONS = 1;
    private int amountOfExecutedTests;

    // Time that the node has been idle, after the max allowed the node will be killed.
    private final static long MAX_IDLE_TIME = 30 * 1000;
    private long lastActivityTime;

    private DockerSeleniumNodePoller dockerSeleniumNodePollerThread = null;


    public DockerSeleniumRemoteProxy(RegistrationRequest request, Registry registry) {
        super(request, registry);
        this.amountOfExecutedTests = 0;
        this.lastActivityTime = System.currentTimeMillis();
    }

    /*
        Incrementing the number of tests that will be executed when the session is assigned.
     */
    @Override
    public TestSession getNewSession(Map<String, Object> requestedCapability) {
        /*
            Validate first if the capability is matched and if there are slots available
         */
        if (!hasCapability(requestedCapability) || getTotalUsed() >= getMaxNumberOfConcurrentTestSessions()) {
            return null;
        }
        if (increaseCounter()) {
            return super.getNewSession(requestedCapability);
        }
        LOGGER.log(Level.FINE, "{0} No more sessions allowed", getNodeIpAndPort());
        return null;
    }

    @Override
    public void startPolling() {
        super.startPolling();
        dockerSeleniumNodePollerThread = new DockerSeleniumNodePoller(this);
        dockerSeleniumNodePollerThread.start();
    }

    @Override
    public void stopPolling() {
        super.stopPolling();
        dockerSeleniumNodePollerThread.interrupt();
    }

    @Override
    public void teardown() {
        super.teardown();
        stopPolling();
    }

    public String getNodeUrl() {
        return "http://" + getRemoteHost().getHost() + ":" + getRemoteHost().getPort();
    }

    public String getNodeIpAndPort() { return getRemoteHost().getHost() + ":" + getRemoteHost().getPort(); }

    public void updateLastActivityTime() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    /*
        Incrementing variable to count the number of tests executed, if possible.
     */
    private synchronized boolean increaseCounter(){
        // Meaning that we have already executed the allowed number of tests.
        if (isTestSessionLimitReached()) {
            return false;
        }
        amountOfExecutedTests++;
        return true;
    }

    /*
        Method to decide if the node can be removed based on the amount of executed tests.
     */
    public synchronized boolean isTestSessionLimitReached() {
        return amountOfExecutedTests >= MAX_UNIQUE_TEST_SESSIONS;
    }

    /*
        Method to decide if the node can be removed based on how long the node has been idle.
    */
    public synchronized boolean isIdleTimeLimitReached() {
        return (System.currentTimeMillis() - this.lastActivityTime) > MAX_IDLE_TIME;
    }


    /*
        Class to poll continuously the node status regarding the amount of tests executed. If MAX_UNIQUE_TEST_SESSIONS
        have been executed, then the node is removed from the grid (this should trigger the docker container to stop).
     */
    static class DockerSeleniumNodePoller extends Thread {

        private DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy = null;

        public DockerSeleniumNodePoller(DockerSeleniumRemoteProxy dockerSeleniumRemoteProxy) {
            this.dockerSeleniumRemoteProxy = dockerSeleniumRemoteProxy;
        }

        @Override
        public void run() {
            while (true) {
                /*
                    If the proxy is not busy and it can be released since the MAX_UNIQUE_TEST_SESSIONS have been executed,
                    then the node executes its teardown.
                */
                if (!dockerSeleniumRemoteProxy.isBusy() && dockerSeleniumRemoteProxy.isTestSessionLimitReached()) {
                    shutdownNode("maxTests");
                    return;
                }

                /*
                    If the proxy is not busy and it can be released since it has been idle longer than the MAX_IDLE_TIME,
                    then the node executes its teardown.
                */
                if (!dockerSeleniumRemoteProxy.isBusy() && dockerSeleniumRemoteProxy.isIdleTimeLimitReached()) {
                    shutdownNode("maxIdle");
                    return;
                }

                /*
                    If the node is busy, we update the last activity time.
                 */
                if (dockerSeleniumRemoteProxy.isBusy()) {
                    dockerSeleniumRemoteProxy.updateLastActivityTime();
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.SEVERE, dockerSeleniumRemoteProxy.getNodeIpAndPort() + " Error while sleeping the " +
                            "thread, stopping thread execution.", e);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private void shutdownNode(String source) {
            String shutdownReason;
            if ("maxTests".equalsIgnoreCase(source)) {
                shutdownReason = String.format("%s Marking the node as down because it was stopped after %s tests.",
                        dockerSeleniumRemoteProxy.getNodeIpAndPort(), MAX_UNIQUE_TEST_SESSIONS);
            } else {
                long seconds = MAX_IDLE_TIME / 1000;
                shutdownReason = String.format("%s Marking the node as down because it has been idle for %s seconds.",
                        dockerSeleniumRemoteProxy.getNodeIpAndPort(), seconds);
            }

            HttpClient client = HttpClientBuilder.create().build();
            String shutDownUrl = dockerSeleniumRemoteProxy.getNodeUrl() +
                    "/selenium-server/driver/?cmd=shutDownSeleniumServer";
            HttpPost post = new HttpPost(shutDownUrl);
            try {
                client.execute(post);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, dockerSeleniumRemoteProxy.getNodeIpAndPort() + " " + e.getMessage(), e);
            } finally {
                dockerSeleniumRemoteProxy.addNewEvent(new RemoteNotReachableException(shutdownReason));
                dockerSeleniumRemoteProxy.addNewEvent(new RemoteUnregisterException(shutdownReason));
                dockerSeleniumRemoteProxy.teardown();
            }
        }

    }




}

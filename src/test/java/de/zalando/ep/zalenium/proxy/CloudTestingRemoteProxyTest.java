package de.zalando.ep.zalenium.proxy;

import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.selenium.remote.server.jmx.JMXHelper;
import org.testng.Assert;

import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

public class CloudTestingRemoteProxyTest {

    @Test
    public void defaultValuesAreAlwaysNull() {
        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
            new JMXHelper().unregister(objectName);
        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
            // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
        }
        GridRegistry registry = new SimpleRegistry();
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30003,
                CloudTestingRemoteProxy.class.getCanonicalName());
        CloudTestingRemoteProxy proxy = CloudTestingRemoteProxy.getNewInstance(request, registry);

        Assert.assertNull(proxy.getAccessKeyProperty());
        Assert.assertNull(proxy.getAccessKeyValue());
        Assert.assertNotNull(proxy.getCloudTestingServiceUrl());
        Assert.assertNull(proxy.getUserNameProperty());
        Assert.assertNull(proxy.getUserNameValue());
        Assert.assertNotNull(proxy.getRemoteHost());
        Assert.assertNull(proxy.getVideoFileExtension());
        Assert.assertNull(proxy.getProxyName());
        Assert.assertNull(proxy.getProxyClassName());
        Assert.assertNull(proxy.getTestInformation("seleniumSessionId"));
    }

}

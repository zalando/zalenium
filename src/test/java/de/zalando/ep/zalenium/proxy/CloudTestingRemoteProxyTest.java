package de.zalando.ep.zalenium.proxy;

import de.zalando.ep.zalenium.util.TestUtils;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.DefaultGridRegistry;
import org.openqa.grid.internal.GridRegistry;
import org.testng.Assert;

public class CloudTestingRemoteProxyTest {

    @Test
    public void defaultValuesAreAlwaysNull() {
        GridRegistry registry = DefaultGridRegistry.newInstance();
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

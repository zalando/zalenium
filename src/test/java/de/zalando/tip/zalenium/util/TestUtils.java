package de.zalando.tip.zalenium.util;

import org.openqa.grid.common.GridRole;
import org.openqa.grid.common.RegistrationRequest;

public class TestUtils {

    public static RegistrationRequest getRegistrationRequestForTesting(final int port, String proxyClass) {
        RegistrationRequest request = new RegistrationRequest();
        request.setRole(GridRole.NODE);
        request.getConfiguration().put(RegistrationRequest.MAX_SESSION, 5);
        request.getConfiguration().put(RegistrationRequest.AUTO_REGISTER, true);
        request.getConfiguration().put(RegistrationRequest.REGISTER_CYCLE, 5000);
        request.getConfiguration().put(RegistrationRequest.HUB_HOST, "localhost");
        request.getConfiguration().put(RegistrationRequest.HUB_PORT, 4444);
        request.getConfiguration().put(RegistrationRequest.PORT, port);
        request.getConfiguration().put(RegistrationRequest.PROXY_CLASS, proxyClass);
        String remoteHost = "http://localhost:" + port;
        request.getConfiguration().put(RegistrationRequest.REMOTE_HOST, remoteHost);
        return request;
    }

}

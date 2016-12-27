package de.zalando.tip.zalenium.util;

import com.beust.jcommander.JCommander;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.utils.configuration.GridNodeConfiguration;

public class TestUtils {

    public static RegistrationRequest getRegistrationRequestForTesting(final int port, String proxyClass) {
        GridNodeConfiguration nodeConfiguration = new GridNodeConfiguration();
        new JCommander(nodeConfiguration, "-role", "wd", "-hubHost", "localhost", "-hubPort", "4444",
                "-host","localhost", "-port", String.valueOf(port), "-proxy", proxyClass, "-registerCycle", "5000",
                "-maxSession", "5");

        return RegistrationRequest.build(nodeConfiguration);
    }
}

package de.zalando.tip.zalenium.util;

public class Environment {

    public String getEnvVariable(String envVariableName) {
        return System.getenv(envVariableName);
    }

}

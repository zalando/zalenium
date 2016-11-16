package de.zalando.tip.zalenium.util;

import com.google.common.annotations.VisibleForTesting;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Environment {

    private final Logger LOGGER = Logger.getLogger(Environment.class.getName());

    private static final String envVarIsNotSetMessage = "Env. variable %s value is not set, falling back to default: %s.";
    private static final String envVarIsNotAValidDataTypeMessage = "Env. variable %s is not a valid %s.";

    @VisibleForTesting
    public String getEnvVariable(String envVariableName) {
        return System.getenv(envVariableName);
    }

    public int getIntEnvVariable(String envVariableName, int defaultValue) {
        if (getEnvVariable(envVariableName) != null) {
            try {
                return Integer.parseInt(getEnvVariable(envVariableName));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, String.format(envVarIsNotAValidDataTypeMessage, envVariableName, "integer"), e);
                return defaultValue;
            }
        } else {
            LOGGER.log(Level.FINE, String.format(envVarIsNotSetMessage, envVariableName, defaultValue));
            return defaultValue;
        }
    }

    public String getStringEnvVariable(String envVariableName, String defaultValue) {
        if (getEnvVariable(envVariableName) != null) {
            try {
                return String.valueOf(getEnvVariable(envVariableName));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, String.format(envVarIsNotAValidDataTypeMessage, envVariableName, "String"), e);
                return defaultValue;
            }
        } else {
            LOGGER.log(Level.FINE, String.format(envVarIsNotSetMessage, envVariableName, defaultValue));
            return defaultValue;
        }
    }

    public Boolean getBooleanEnvVariable(String envVariableName, Boolean defaultValue) {
        if (getEnvVariable(envVariableName) != null) {
            String var = getEnvVariable(envVariableName).toLowerCase();
            if("true".equalsIgnoreCase(var) || "false".equalsIgnoreCase(var)) {
                return Boolean.parseBoolean(var);
            } else{
                LOGGER.log(Level.WARNING, String.format(envVarIsNotAValidDataTypeMessage, envVariableName, "boolean"));
                return defaultValue;
            }
        } else {
            LOGGER.log(Level.FINE, String.format(envVarIsNotSetMessage, envVariableName, defaultValue));
            return defaultValue;
        }
    }

}

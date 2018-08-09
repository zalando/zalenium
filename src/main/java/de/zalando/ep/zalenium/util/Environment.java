package de.zalando.ep.zalenium.util;

import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Environment {

    private final Logger logger = LoggerFactory.getLogger(Environment.class.getName());

    private static final String ENV_VAR_IS_NOT_SET = "Env. variable %s value is not set, falling back to default: %s";
    private static final String ENV_VAR_IS_NOT_A_VALID_DATA_TYPE = "Env. variable %s is not a valid %s";

    @VisibleForTesting
    public String getEnvVariable(String envVariableName) {
        return System.getenv(envVariableName);
    }

    public int getIntEnvVariable(String envVariableName, int defaultValue) {
        if (getEnvVariable(envVariableName) != null) {
            try {
                return Integer.parseInt(getEnvVariable(envVariableName));
            } catch (Exception e) {
                logger.warn(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "integer"));
                logger.debug(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "integer"), e);
                return defaultValue;
            }
        } else {
            logger.debug(String.format(ENV_VAR_IS_NOT_SET, envVariableName, defaultValue));
            return defaultValue;
        }
    }

    public String getStringEnvVariable(String envVariableName, String defaultValue) {
        if (getEnvVariable(envVariableName) != null && !getEnvVariable(envVariableName).isEmpty()) {
            try {
                return String.valueOf(getEnvVariable(envVariableName));
            } catch (Exception e) {
                logger.warn(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "String"));
                logger.debug(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "String"), e);
                return defaultValue;
            }
        } else {
            logger.debug(String.format(ENV_VAR_IS_NOT_SET, envVariableName, defaultValue));
            return defaultValue;
        }
    }

    public Boolean getBooleanEnvVariable(String envVariableName, Boolean defaultValue) {
        if (getEnvVariable(envVariableName) != null) {
            String var = getEnvVariable(envVariableName).toLowerCase();
            if("true".equalsIgnoreCase(var) || "false".equalsIgnoreCase(var)) {
                return Boolean.parseBoolean(var);
            } else{
                logger.warn(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "boolean"));
                return defaultValue;
            }
        } else {
            logger.debug(String.format(ENV_VAR_IS_NOT_SET, envVariableName, defaultValue));
            return defaultValue;
        }
    }

    public double[] getDoubleArrayEnvVariable(String envVariableName, double... defaultValues) {
        String envVariable = getEnvVariable(envVariableName);
        double[] buckets;
        if (envVariable != null) {
            String[] bucketParams = envVariable.split(",");
            buckets = new double[bucketParams.length];

            for (int i = 0; i < bucketParams.length; i++) {
                buckets[i] = Double.parseDouble(bucketParams[i]);
            }

        }
        else {
            buckets = defaultValues;
        }

        return buckets;
    }
}

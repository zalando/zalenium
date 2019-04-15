package de.zalando.ep.zalenium.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public <T> List<T> getYamlListEnvVariable(String envVariableName, Class<T> clazz, List<T> defaultValues) {
        String envVariable = getEnvVariable(envVariableName);

        if (envVariable != null) {
            try {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

                CollectionType javaType = mapper.getTypeFactory()
                        .constructCollectionType(List.class, clazz);
                return mapper.readValue(envVariable, javaType);
            } catch (IOException e) {
                logger.warn("Unable to parse Yaml type from env variable. Falling back to default values.", e);
                logger.warn(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "yamlList"));
                logger.debug(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "yamlList"), e);
                return defaultValues;
            }
        } else {
            return defaultValues;
        }
    }

    public Map<String,String> getMapEnvVariable(String envVariableName, Map<String,String> defaultValues) {
        String envVariable = getEnvVariable(envVariableName);

        if (envVariable != null) {
            try {
                Map<String,String> values = Stream.of(envVariable.split(","))
                        .map(str -> str.split("="))
                        .collect(Collectors.toMap(str -> str[0], str -> str[1]));

                return values;
            } catch (Exception e) {
                logger.warn("Unable to parse Yaml type from env variable. Falling back to default values.", e);
                logger.warn(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "yamlMap"));
                logger.debug(String.format(ENV_VAR_IS_NOT_A_VALID_DATA_TYPE, envVariableName, "yamlMap"), e);
                return defaultValues;
            }
        }
        else {
            return defaultValues;
        }
    }

    public String getContextPath() {
        String contextPath = getStringEnvVariable("CONTEXT_PATH", "");
        contextPath = contextPath.trim();
        // To load static files(css and images), if it is only '/'
        // do not add contextPath in the generated html
        if ("/".equals(contextPath)) {
            contextPath = "";
        }
        return contextPath;
    }
}

package io.agh.iot.sim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private String targetBaseUrl = "http://iot-agent-device-api:8080";
    private int defaultCount = 5;
    private String devicePrefix = "sim";
    private long publishIntervalMs = 5000;
    private String mqttBrokerUri = "tcp://iot-agent-mqtt-broker:1883";
    private boolean mixedModeEnabled = true;
    private AutoRegister autoRegister = new AutoRegister();

    public String getTargetBaseUrl() {
        return targetBaseUrl;
    }

    public void setTargetBaseUrl(String targetBaseUrl) {
        this.targetBaseUrl = targetBaseUrl;
    }

    public int getDefaultCount() {
        return defaultCount;
    }

    public void setDefaultCount(int defaultCount) {
        this.defaultCount = defaultCount;
    }

    public String getDevicePrefix() {
        return devicePrefix;
    }

    public void setDevicePrefix(String devicePrefix) {
        this.devicePrefix = devicePrefix;
    }

    public AutoRegister getAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(AutoRegister autoRegister) {
        this.autoRegister = autoRegister;
    }

    public long getPublishIntervalMs() {
        return publishIntervalMs;
    }

    public void setPublishIntervalMs(long publishIntervalMs) {
        this.publishIntervalMs = publishIntervalMs;
    }

    public String getMqttBrokerUri() {
        return mqttBrokerUri;
    }

    public void setMqttBrokerUri(String mqttBrokerUri) {
        this.mqttBrokerUri = mqttBrokerUri;
    }

    public boolean isMixedModeEnabled() {
        return mixedModeEnabled;
    }

    public void setMixedModeEnabled(boolean mixedModeEnabled) {
        this.mixedModeEnabled = mixedModeEnabled;
    }

    public static class AutoRegister {
        private boolean enabled = true;
        private int count = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
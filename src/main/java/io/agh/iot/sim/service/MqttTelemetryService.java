package io.agh.iot.sim.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agh.iot.sim.config.SimulatorProperties;
import io.agh.iot.sim.model.DeviceProfile;
import io.agh.iot.sim.model.DeviceStatusMessage;
import io.agh.iot.sim.model.TelemetrySample;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class MqttTelemetryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttTelemetryService.class);

    private final DeviceSimulationService deviceSimulationService;
    private final SimulatorProperties simulatorProperties;
    private final ObjectMapper objectMapper;

    private final Map<String, Double> tempPhase = new ConcurrentHashMap<>();
    private final Map<String, Integer> batteryByDevice = new ConcurrentHashMap<>();
    private final AtomicLong publishedSamples = new AtomicLong();

    private MqttClient mqttClient;

    public MqttTelemetryService(DeviceSimulationService deviceSimulationService,
                                SimulatorProperties simulatorProperties,
                                ObjectMapper objectMapper) {
        this.deviceSimulationService = deviceSimulationService;
        this.simulatorProperties = simulatorProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        connectClient();
    }

    @PreDestroy
    public void shutdown() {
        if (mqttClient == null) {
            return;
        }
        try {
            mqttClient.disconnect();
            mqttClient.close();
        } catch (MqttException exception) {
            LOGGER.warn("Failed to close MQTT client cleanly: {}", exception.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${simulator.publish-interval-ms:5000}")
    public void publishTelemetry() {
        ensureConnected();

        List<DeviceProfile> devices = deviceSimulationService.getActiveDevices();
        if (devices.isEmpty()) {
            devices = deviceSimulationService.refreshActiveDevices();
        }

        long timestamp = System.currentTimeMillis();
        for (DeviceProfile device : devices) {
            TelemetrySample sample = buildSample(device, timestamp);
            DeviceStatusMessage statusMessage = buildStatus(device, timestamp);

            publishJson("devices/%s/telemetry".formatted(device.id()), sample);
            publishJson("devices/%s/status".formatted(device.id()), statusMessage);

            LOGGER.info(
                "sim.publish device={} status={} temp={} humidity={} co2={} pm25={} energy={} zone={} leak={}",
                device.id(),
                statusMessage.status(),
                sample.temperatureC(),
                sample.humidityPct(),
                sample.co2Ppm(),
                sample.pm25(),
                sample.energyUsageW(),
                sample.krakowZone(),
                sample.leak()
            );
            publishedSamples.incrementAndGet();
        }

        LOGGER.info("sim.cycle devices={} totalPublished={}", devices.size(), publishedSamples.get());
    }

    private void connectClient() {
        try {
            mqttClient = new MqttClient(simulatorProperties.getMqttBrokerUri(), MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            mqttClient.connect(options);
            LOGGER.info("sim.mqtt.connected broker={}", simulatorProperties.getMqttBrokerUri());
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    LOGGER.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleCommand(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // no-op
                }
            });
            mqttClient.subscribe("devices/+/command", 1);
            LOGGER.info("sim.mqtt.subscribed topic=devices/+/command");
        } catch (MqttException exception) {
            throw new IllegalStateException("Unable to connect simulator to MQTT broker", exception);
        }
    }

    private void ensureConnected() {
        if (mqttClient != null && mqttClient.isConnected()) {
            return;
        }
        connectClient();
    }

    private TelemetrySample buildSample(DeviceProfile device, long timestamp) {
        double phase = tempPhase.compute(device.id(), (id, current) -> current == null ? 0.0 : current + 1.0);
        double noise = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);

        Double temperature = null;
        if (Boolean.TRUE.equals(device.temperatureSensorEnabled())) {
            temperature = 20 + Math.sin(phase / 60.0) * 2 + noise;
        }

        Double humidity = null;
        if (Boolean.TRUE.equals(device.humiditySensorEnabled())) {
            humidity = 52 + Math.sin(phase / 45.0) * 8 + ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        }

        Double co2 = null;
        Double pm25 = null;
        if (Boolean.TRUE.equals(device.airQualitySensorEnabled())) {
            co2 = 480 + Math.sin(phase / 35.0) * 110 + ThreadLocalRandom.current().nextDouble(-18, 18);
            pm25 = 9 + Math.sin(phase / 40.0) * 4 + ThreadLocalRandom.current().nextDouble(-1.2, 1.2);
        }

        double energyUsageW = 50 + Math.max(0.0, (temperature != null ? temperature - 18 : 0.0)) * 6
            + ThreadLocalRandom.current().nextDouble(0, 6);

        boolean leak = Boolean.TRUE.equals(device.mapSensorEnabled()) && ThreadLocalRandom.current().nextDouble() < leakProbability(device.krakowZone());

        return new TelemetrySample(
            device.id(),
            timestamp,
            round(temperature),
            round(humidity),
            round(co2),
            round(pm25),
            round(energyUsageW),
            device.krakowZone(),
            leak
        );
    }

    private DeviceStatusMessage buildStatus(DeviceProfile device, long timestamp) {
        int battery = batteryByDevice.compute(device.id(), (id, current) -> {
            int value = current == null ? ThreadLocalRandom.current().nextInt(70, 100) : current;
            return Math.max(20, value - ThreadLocalRandom.current().nextInt(0, 2));
        });

        String status = device.status();
        if (status == null || status.isBlank()) {
            status = "ONLINE";
        }

        return new DeviceStatusMessage(device.id(), timestamp, status, battery, device.krakowZone());
    }

    private void publishJson(String topic, Object payload) {
        try {
            byte[] body = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            mqttClient.publish(topic, new MqttMessage(body));
        } catch (Exception exception) {
            LOGGER.warn("Failed to publish {}: {}", topic, exception.getMessage());
        }
    }

    private void handleCommand(String topic, MqttMessage message) {
        try {
            String[] segments = topic.split("/");
            if (segments.length < 3) {
                return;
            }
            String deviceId = segments[1];
            Map<String, Object> cmd = objectMapper.readValue(message.getPayload(), new TypeReference<>() {});
            String type = String.valueOf(cmd.get("type"));

            if ("CHANGE_INTERVAL".equalsIgnoreCase(type) && cmd.get("sampleIntervalMs") instanceof Number interval) {
                deviceSimulationService.updateInterval(deviceId, Math.max(500, interval.intValue()));
                LOGGER.info("sim.command device={} type={} sampleIntervalMs={}", deviceId, type, interval.intValue());
            } else if ("FORCE_STATUS".equalsIgnoreCase(type) && cmd.get("status") != null) {
                deviceSimulationService.updateStatus(deviceId, String.valueOf(cmd.get("status")));
                LOGGER.info("sim.command device={} type={} status={}", deviceId, type, cmd.get("status"));
            }
        } catch (Exception exception) {
            LOGGER.warn("Ignoring invalid command payload: {}", exception.getMessage());
        }
    }

    private static double leakProbability(String zone) {
        if (zone == null) {
            return 0.01;
        }
        return switch (zone) {
            case "NOWA_HUTA" -> 0.03;
            case "PODGORZE" -> 0.02;
            default -> 0.01;
        };
    }

    private static Double round(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }
}

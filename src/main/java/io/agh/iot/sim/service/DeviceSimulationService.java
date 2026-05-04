package io.agh.iot.sim.service;

import io.agh.iot.sim.config.SimulatorProperties;
import io.agh.iot.sim.model.DeviceProfile;
import io.agh.iot.sim.model.SimulatedDeviceRequest;
import io.agh.iot.sim.model.SimulationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

@Service
public class DeviceSimulationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceSimulationService.class);
    private static final List<String> KRAKOW_ZONES = List.of("KROWODRZA", "PODGORZE", "NOWA_HUTA", "STARE_MIASTO");

    private final RestClient restClient;
    private final SimulatorProperties simulatorProperties;
    private final ConcurrentMap<String, DeviceProfile> activeDevices = new ConcurrentHashMap<>();

    public DeviceSimulationService(RestClient restClient, SimulatorProperties simulatorProperties) {
        this.restClient = restClient;
        this.simulatorProperties = simulatorProperties;
    }

    public SimulationResult registerDevices(Integer count, String requestedPrefix) {
        int devicesToCreate = count == null || count < 1 ? simulatorProperties.getDefaultCount() : count;
        String prefix = requestedPrefix == null || requestedPrefix.isBlank()
            ? simulatorProperties.getDevicePrefix()
            : requestedPrefix;

        List<SimulatedDeviceRequest> devices = IntStream.range(0, devicesToCreate)
            .mapToObj(index -> createDevice(prefix, index + 1))
            .toList();

        int createdCount = 0;
        for (SimulatedDeviceRequest device : devices) {
            try {
                restClient.post()
                    .uri("/devices")
                    .body(device)
                    .retrieve()
                    .toBodilessEntity();
                createdCount++;
            } catch (RestClientException exception) {
                LOGGER.warn("Failed to register device {}: {}", device.id(), exception.getMessage());
            }
        }

        return new SimulationResult(createdCount, devices);
    }

    public List<DeviceProfile> getActiveDevices() {
        return new ArrayList<>(activeDevices.values());
    }

    public List<DeviceProfile> refreshActiveDevices() {
        List<DeviceProfile> remote = fetchDevices();
        if (remote.isEmpty() && !activeDevices.isEmpty()) {
            return new ArrayList<>(activeDevices.values());
        }

        if (simulatorProperties.isMixedModeEnabled()) {
            int desired = simulatorProperties.getAutoRegister().getCount();
            int missing = Math.max(0, desired - remote.size());
            if (missing > 0 && simulatorProperties.getAutoRegister().isEnabled()) {
                try {
                    registerDevices(missing, simulatorProperties.getDevicePrefix());
                } catch (RuntimeException exception) {
                    LOGGER.warn("Auto-registration skipped: {}", exception.getMessage());
                }
                remote = fetchDevices();
            }
        }

        activeDevices.clear();
        remote.forEach(device -> activeDevices.put(device.id(), device));
        return new ArrayList<>(activeDevices.values());
    }

    public void updateInterval(String deviceId, int sampleIntervalMs) {
        activeDevices.computeIfPresent(deviceId, (id, profile) -> new DeviceProfile(
            profile.id(),
            profile.name(),
            profile.status(),
            profile.krakowZone(),
            sampleIntervalMs,
            profile.temperatureSensorEnabled(),
            profile.humiditySensorEnabled(),
            profile.airQualitySensorEnabled(),
            profile.mapSensorEnabled()
        ));
    }

    public void updateStatus(String deviceId, String status) {
        activeDevices.computeIfPresent(deviceId, (id, profile) -> new DeviceProfile(
            profile.id(),
            profile.name(),
            status,
            profile.krakowZone(),
            profile.sampleIntervalMs(),
            profile.temperatureSensorEnabled(),
            profile.humiditySensorEnabled(),
            profile.airQualitySensorEnabled(),
            profile.mapSensorEnabled()
        ));
    }

    private List<DeviceProfile> fetchDevices() {
        try {
            DeviceProfile[] response = restClient.get()
                .uri("/devices")
                .retrieve()
                .body(DeviceProfile[].class);
            if (response == null) {
                return List.of();
            }
            return List.of(response);
        } catch (RestClientException exception) {
            LOGGER.warn("Could not fetch devices from device-api: {}", exception.getMessage());
            return List.of();
        }
    }

    private SimulatedDeviceRequest createDevice(String prefix, int index) {
        String baseId = "%s-%03d".formatted(prefix, index);
        String uniqueId = "%s-%s".formatted(baseId, UUID.randomUUID().toString().substring(0, 8));
        String zone = KRAKOW_ZONES.get(index % KRAKOW_ZONES.size());
        return new SimulatedDeviceRequest(
            uniqueId,
            "Simulated %s %d".formatted(prefix, index),
            "ONLINE",
            zone,
            5000,
            true,
            true,
            true,
            true
        );
    }
}
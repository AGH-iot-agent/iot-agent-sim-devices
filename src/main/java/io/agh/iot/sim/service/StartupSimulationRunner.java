package io.agh.iot.sim.service;

import io.agh.iot.sim.config.SimulatorProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StartupSimulationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupSimulationRunner.class);

    private final DeviceSimulationService deviceSimulationService;
    private final SimulatorProperties simulatorProperties;

    public StartupSimulationRunner(DeviceSimulationService deviceSimulationService,
                                   SimulatorProperties simulatorProperties) {
        this.deviceSimulationService = deviceSimulationService;
        this.simulatorProperties = simulatorProperties;
    }

    @PostConstruct
    public void autoRegisterDevices() {
        try {
            if (simulatorProperties.getAutoRegister().isEnabled()) {
                deviceSimulationService.registerDevices(
                    simulatorProperties.getAutoRegister().getCount(),
                    simulatorProperties.getDevicePrefix()
                );
            }
            deviceSimulationService.refreshActiveDevices();
        } catch (RuntimeException exception) {
            LOGGER.warn("Automatic device registration failed: {}", exception.getMessage());
        }
    }
}
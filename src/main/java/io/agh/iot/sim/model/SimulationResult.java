package io.agh.iot.sim.model;

import java.util.List;

public record SimulationResult(int createdCount, List<SimulatedDeviceRequest> devices) {
}
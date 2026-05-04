package io.agh.iot.sim.model;

public record DeviceStatusMessage(
    String deviceId,
    long timestamp,
    String status,
    int batteryPct,
    String krakowZone
) {
}

package io.agh.iot.sim.model;

public record TelemetrySample(
    String deviceId,
    long timestamp,
    Double temperatureC,
    Double humidityPct,
    Double co2Ppm,
    Double pm25,
    Double energyUsageW,
    String krakowZone,
    boolean leak
) {
}

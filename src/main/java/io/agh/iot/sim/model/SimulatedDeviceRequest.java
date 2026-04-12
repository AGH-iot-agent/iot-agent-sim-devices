package io.agh.iot.sim.model;

public record SimulatedDeviceRequest(
	String id,
	String name,
	String status,
	String krakowZone,
	Integer sampleIntervalMs,
	Boolean temperatureSensorEnabled,
	Boolean humiditySensorEnabled,
	Boolean airQualitySensorEnabled,
	Boolean mapSensorEnabled
) {
}
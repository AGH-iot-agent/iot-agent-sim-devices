package io.agh.iot.sim.controller;

import io.agh.iot.sim.model.SimulationRequest;
import io.agh.iot.sim.model.SimulationResult;
import io.agh.iot.sim.service.DeviceSimulationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulations")
public class SimulationController {

    private final DeviceSimulationService deviceSimulationService;

    public SimulationController(DeviceSimulationService deviceSimulationService) {
        this.deviceSimulationService = deviceSimulationService;
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationResult registerDevices(@RequestBody(required = false) SimulationRequest request) {
        Integer count = request != null ? request.count() : null;
        String devicePrefix = request != null ? request.devicePrefix() : null;
        return deviceSimulationService.registerDevices(count, devicePrefix);
    }
}
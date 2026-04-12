package io.agh.iot.sim;

import io.agh.iot.sim.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
@EnableScheduling
public class SimDevicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimDevicesApplication.class, args);
    }
}

package io.agh.iot.sim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient restClient(RestClient.Builder builder,
                          SimulatorProperties simulatorProperties) {
        return builder
            .baseUrl(simulatorProperties.getTargetBaseUrl())
            .build();
    }
}
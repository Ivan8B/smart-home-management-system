package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CityPowerInputConfiguration {
    @Value("${cityPowerInput.relay.address}")
    private Integer address;

    @Value("${cityPowerInput.relay.discreteInput}")
    private Integer discreteInput;

    public Integer getAddress() {
        return address;
    }

    public Integer getDiscreteInput() {
        return discreteInput;
    }

}

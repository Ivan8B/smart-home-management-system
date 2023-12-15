package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElectricBoilerConfiguration {
    @Value("${electricBoiler.relay.address}")
    private Integer address;

    @Value("${electricBoiler.relay.coil}")
    private Integer coil;

    @Value("${electricBoiler.hysteresis}")
    private Float hysteresis;

    public Integer getAddress() {
        return address;
    }

    public Integer getCoil() {
        return coil;
    }

    public Float getHysteresis() {
        return hysteresis;
    }
}

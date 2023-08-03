package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FunnelHeatingConfiguration {
    @Value("${funnelHeating.relay.address}")
    private Integer ADDRESS;

    @Value("${funnelHeating.relay.coil}")
    private Integer COIL;

    @Value("${funnelHeating.temperature.min}")
    private Float TEMPERATURE_MIN;

    @Value("${funnelHeating.temperature.max}")
    private Float TEMPERATURE_MAX;

    public Integer getAddress() {
        return ADDRESS;
    }

    public Integer getCoil() {
        return COIL;
    }

    public Float getTemperatureMin() {
        return TEMPERATURE_MIN;
    }

    public Float getTemperatureMax() {
        return TEMPERATURE_MAX;
    }
}

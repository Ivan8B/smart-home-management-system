package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FunnelHeatingConfiguration {
    @Value("${funnelHeating.relay.address}")
    private Integer address;

    @Value("${funnelHeating.relay.coil}")
    private Integer coil;

    @Value("${funnelHeating.temperature.min}")
    private Float temperatureMin;

    @Value("${funnelHeating.temperature.max}")
    private Float temperatureMax;

    public Integer getAddress() {
        return address;
    }

    public Integer getCoil() {
        return coil;
    }

    public Float getTemperatureMin() {
        return temperatureMin;
    }

    public Float getTemperatureMax() {
        return temperatureMax;
    }
}

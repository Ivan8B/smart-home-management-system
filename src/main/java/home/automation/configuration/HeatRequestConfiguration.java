package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HeatRequestConfiguration {
    @Value("${temperature.target}")
    private Float temperatureOutsideDisable;

    @Value("${temperature.hysteresis}")
    private Float hysteresis;

    public Float getTemperatureOutsideDisable() {
        return temperatureOutsideDisable;
    }

    public Float getHysteresis() {
        return hysteresis;
    }
}

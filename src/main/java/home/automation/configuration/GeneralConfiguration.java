package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeneralConfiguration {
    @Value("${temperature.target}")
    private Float targetTemperature;

    @Value("${temperature.outsideMin}")
    private Float outsideMin;

    @Value("${temperature.hysteresis}")
    private Float hysteresis;

    public Float getTargetTemperature() {
        return targetTemperature;
    }

    public Float getOutsideMin() {
        return outsideMin;
    }

    public Float getHysteresis() {
        return hysteresis;
    }
}

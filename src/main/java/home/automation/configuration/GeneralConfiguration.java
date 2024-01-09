package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeneralConfiguration {
    @Value("${temperature.insideTarget}")
    private Float insideTarget;

    @Value("${temperature.outsideMax}")
    private Float outsideMax;

    @Value("${temperature.outsideMin}")
    private Float outsideMin;

    @Value("${temperature.hysteresis}")
    private Float hysteresis;

    public Float getInsideTarget() {
        return insideTarget;
    }

    public Float getOutsideMax() {
        return outsideMax;
    }

    public Float getOutsideMin() {
        return outsideMin;
    }

    public Float getHysteresis() {
        return hysteresis;
    }
}

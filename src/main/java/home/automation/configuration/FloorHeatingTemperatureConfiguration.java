package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FloorHeatingTemperatureConfiguration {
    @Value("${floorHeating.temperature.direct.max}")
    private Float directMaxTemperature;

    @Value("${floorHeating.temperature.maxDelta}")
    private Float maxDelta;

    @Value("${floorHeating.temperature.k}")
    private Float k;

    @Value("${floorHeating.temperature.accuracy}")
    private Float accuracy;

    public Float getDirectMaxTemperature() {
        return directMaxTemperature;
    }

    public Float getMaxDelta() {
        return maxDelta;
    }

    public Float getK() {
        return k;
    }

    public Float getAccuracy() {
        return accuracy;
    }
}

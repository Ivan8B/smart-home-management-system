package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FloorHeatingTemperatureConfiguration {
    @Value("${floorHeating.temperature.direct.min}")
    private Float directMinTemperature;

    @Value("${floorHeating.temperature.direct.max}")
    private Float directMaxTemperature;

    @Value("${floorHeating.temperature.k}")
    private Float k;

    @Value("${floorHeating.temperature.accuracy}")
    private Float accuracy;

    public Float getDirectMinTemperature() {
        return directMinTemperature;
    }

    public Float getDirectMaxTemperature() {
        return directMaxTemperature;
    }

    public Float getK() {
        return k;
    }

    public Float getAccuracy() {
        return accuracy;
    }
}

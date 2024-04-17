package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FloorHeatingConfiguration {
    @Value("${floorHeating.valuesCountForAverage}")
    private Integer valuesCountForAverage;

    public Integer getValuesCountForAverage() {
        return valuesCountForAverage;
    }
}

package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class FloorHeatingConfiguration {
    @Value("${floorHeating.valuesCountForAverage}")
    private Integer valuesCountForAverage;

    @Value("${floorHeating.gasBoilerWorkDurationToRotateValve}")
    private Duration gasBoilerWorkDurationToRotateValve;

    public Integer getValuesCountForAverage() {
        return valuesCountForAverage;
    }

    public Duration getGasBoilerWorkDurationToRotateValve() {
        return gasBoilerWorkDurationToRotateValve;
    }
}

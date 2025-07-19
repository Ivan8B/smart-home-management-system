package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class FloorHeatingConfiguration {
    @Value("${floorHeating.idleIntervalToRotate}")
    private Duration idleIntervalToRotate;

    @Value("${floorHeating.valuesCountForAverage}")
    private Integer valuesCountForAverage;

    @Value("${floorHeating.gasBoilerWorkDurationToRotateValve}")
    private Duration gasBoilerWorkDurationToRotateValve;

    public Duration getIdleIntervalToRotate() {
        return idleIntervalToRotate;
    }

    public Integer getValuesCountForAverage() {
        return valuesCountForAverage;
    }

    public Duration getGasBoilerWorkDurationToRotateValve() {
        return gasBoilerWorkDurationToRotateValve;
    }
}

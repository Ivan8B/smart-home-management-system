package home.automation.configuration;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GasBoilerConfiguration {
    @Value("${gasBoiler.relay.address}")
    private Integer address;

    @Value("${gasBoiler.relay.coil}")
    private Integer coil;

    @Value("${gasBoiler.relay.turnOffDelay}")
    private Duration turnOffDelay;

    @Value("${gasBoiler.return.minTemperature}")
    private Float returnMinTemperature;

    @Value("${gasBoiler.waterFlow}")
    private Float waterFlow;

    public Integer getAddress() {
        return address;
    }

    public Integer getCoil() {
        return coil;
    }

    public Duration getTurnOffDelay() {
        return turnOffDelay;
    }

    public Float getReturnMinTemperature() {
        return returnMinTemperature;
    }

    public Float getWaterFlow() {
        return waterFlow;
    }
}

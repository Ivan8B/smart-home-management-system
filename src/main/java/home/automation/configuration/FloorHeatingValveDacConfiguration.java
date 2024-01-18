package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FloorHeatingValveDacConfiguration {
    @Value("${floorHeating.valve.dac.address}")
    private Integer address;

    @Value("${floorHeating.valve.dac.register}")
    private Integer register;

    @Value("${floorHeating.valve.dac.accuracy}")
    private Integer accuracy;

    @Value("${floorHeating.valve.dac.minOpenPercent}")
    private Integer minOpenPercent;

    @Value("${floorHeating.valve.dac.maxOpenPercent}")
    private Integer maxOpenPercent;

    public Integer getAddress() {
        return address;
    }

    public Integer getRegister() {
        return register;
    }

    public Integer getAccuracy() {
        return accuracy;
    }

    public Integer getMinOpenPercent() {
        return minOpenPercent;
    }

    public Integer getMaxOpenPercent() {
        return maxOpenPercent;
    }
}

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
    private Float accuracy;

    public Integer getAddress() {
        return address;
    }

    public Integer getRegister() {
        return register;
    }

    public Float getAccuracy() {
        return accuracy;
    }
}

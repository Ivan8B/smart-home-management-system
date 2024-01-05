package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FloorHeatingValveDacConfiguration {
    @Value("${floorHeating.valve.dac.address}")
    private Integer address;

    @Value("${floorHeating.valve.dac.readRegister}")
    private Integer readRegister;

    @Value("${floorHeating.valve.dac.writeRegister}")
    private Integer writeRegister;

    @Value("${floorHeating.valve.dac.accuracy}")
    private Float accuracy;

    public Integer getAddress() {
        return address;
    }

    public Integer getReadRegister() {
        return readRegister;
    }

    public Integer getWriteRegister() {
        return writeRegister;
    }

    public Float getAccuracy() {
        return accuracy;
    }
}

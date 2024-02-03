package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GasBoilerFakeOutsideTemperatureConfiguration {
    @Value("${gasBoiler.fakeOutsideTemperature.mainRelay.address}")
    private Integer mainAddress;

    @Value("${gasBoiler.fakeOutsideTemperature.mainRelay.coil}")
    private Integer mainCoil;

    @Value("${gasBoiler.fakeOutsideTemperature.secondaryRelay.address}")
    private Integer secondaryAddress;

    @Value("${gasBoiler.fakeOutsideTemperature.secondaryRelay.coil}")
    private Integer secondaryCoil;

    public Integer getMainAddress() {
        return mainAddress;
    }

    public Integer getMainCoil() {
        return mainCoil;
    }

    public Integer getSecondaryAddress() {
        return secondaryAddress;
    }

    public Integer getSecondaryCoil() {
        return secondaryCoil;
    }
}

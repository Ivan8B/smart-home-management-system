package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class WaterLeakConfiguration {
    @Value("${waterLeak.sensors.address}")
    private Integer sensorsAddress;

    @Value("${waterLeak.sensors.registerStart}")
    private Integer sensorsRegisterStart;

    @Value("${waterLeak.pumpRelay.address}")
    private Integer pumpRelayAddress;

    @Value("${waterLeak.pumpRelay.coil}")
    private Integer pumpRelayCoil;

    @Value("${waterLeak.outputs.address}")
    private Integer outputsAddress;

    @Value("${waterLeak.outputs.filterValvePowerRegister}")
    private Integer filterValvePowerRegister;

    @Value("${waterLeak.outputs.filterValveCommandRegister}")
    private Integer filterValveCommandRegister;

    @Value("${waterLeak.outputs.cityValvePowerRegister}")
    private Integer cityValvePowerRegister;

    @Value("${waterLeak.outputs.cityValveCommandRegister}")
    private Integer cityValveCommandRegister;

    @Value("${waterLeak.outputs.filterValvePowerDuration}")
    private Duration filterValvePowerDuration;

    @Value("${waterLeak.outputs.cityValvePowerDuration}")
    private Duration cityValvePowerDuration;

    public Integer getSensorsAddress() {
        return sensorsAddress;
    }

    public Integer getSensorsRegisterStart() {
        return sensorsRegisterStart;
    }

    public Integer getPumpRelayAddress() {
        return pumpRelayAddress;
    }

    public Integer getPumpRelayCoil() {
        return pumpRelayCoil;
    }

    public Integer getOutputsAddress() {
        return outputsAddress;
    }

    public Integer getFilterValvePowerRegister() {
        return filterValvePowerRegister;
    }

    public Integer getFilterValveCommandRegister() {
        return filterValveCommandRegister;
    }

    public Integer getCityValvePowerRegister() {
        return cityValvePowerRegister;
    }

    public Integer getCityValveCommandRegister() {
        return cityValveCommandRegister;
    }

    public Duration getFilterValvePowerDuration() {
        return filterValvePowerDuration;
    }

    public Duration getCityValvePowerDuration() {
        return cityValvePowerDuration;
    }
}

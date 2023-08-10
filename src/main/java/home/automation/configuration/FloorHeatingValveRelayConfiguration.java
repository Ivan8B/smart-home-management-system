package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FloorHeatingValveRelayConfiguration {
    @Value("${floorHeating.valve.relay.address}")
    private Integer address;

    @Value("${floorHeating.valve.relay.coil}")
    private Integer coil;

    @Value("${floorHeating.valve.relay.delay}")
    private Integer delay;

    public Integer getAddress() {
        return address;
    }

    public Integer getCoil() {
        return coil;
    }

    public Integer getDelay() {
        return delay;
    }
}

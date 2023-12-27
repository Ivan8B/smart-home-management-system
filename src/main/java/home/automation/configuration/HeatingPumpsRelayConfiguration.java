package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HeatingPumpsRelayConfiguration {
    @Value("${heatingPumps.relay.address}")
    private Integer address;

    @Value("${heatingPumps.relay.coil}")
    private Integer coil;

    public Integer getAddress() {
        return address;
    }

    public Integer getCoil() {
        return coil;
    }
}

package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GasBoilerConfiguration {
    @Value("${gasBoiler.relay.address}")
    private Integer ADDRESS;

    @Value("${gasBoiler.relay.coil}")
    private Integer COIL;

    public Integer getAddress() {
        return ADDRESS;
    }

    public Integer getCoil() {
        return COIL;
    }
}

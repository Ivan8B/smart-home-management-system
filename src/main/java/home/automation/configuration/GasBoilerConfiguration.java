package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GasBoilerConfiguration {
    @Value("${gasBoilerRelay.address}")
    private Integer ADDRESS;

    @Value("${gasBoilerRelay.coil}")
    private Integer COIL;

    public Integer getAddress() {
        return ADDRESS;
    }

    public Integer getCoil() {
        return COIL;
    }
}

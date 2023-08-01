package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StreetLightConfiguration {
    @Value("${streetLight.relay.address}")
    private Integer ADDRESS;

    @Value("${streetLight.relay.coil}")
    private Integer COIL;

    @Value("${streetLight.latitude}")
    private Double latitude;

    @Value("${streetLight.longitude}")
    private Double longitude;

    public Integer getAddress() {
        return ADDRESS;
    }

    public Integer getCoil() {
        return COIL;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}

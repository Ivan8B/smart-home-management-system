package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StreetLightConfiguration {
    @Value("${streetLight.relay.address}")
    private Integer address;

    @Value("${streetLight.relay.coil}")
    private Integer coil;

    @Value("${streetLight.latitude}")
    private Double latitude;

    @Value("${streetLight.longitude}")
    private Double longitude;

    public Integer getAddress() {
        return address;
    }

    public Integer getCoil() {
        return coil;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}

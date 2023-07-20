package home.heating.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemperatureSensorsBoardConfiguration {
    @Value("${temperatureSensorsBoard.address}")
    private Integer ADDRESS;

    public Integer getAddress() {
        return ADDRESS;
    }
}

package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemperatureSensorsBoardsConfiguration {

    @Value("${temperatureSensorsBoard." + FIRST_BOARD_NAME + ".address}")
    private Integer firstAddress;

    public static final String FIRST_BOARD_NAME = "board1";

    public Integer getAddressByName(String name) {
        if (FIRST_BOARD_NAME.equals(name)) {
            return firstAddress;
        }
        return null;
    }
}

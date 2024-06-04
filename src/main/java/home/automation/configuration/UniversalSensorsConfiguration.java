package home.automation.configuration;

import home.automation.enums.UniversalSensor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties("")
public class UniversalSensorsConfiguration {
    private final String ADDRESS_STRING = "address";
    private final Map<String, Integer> universalSensorAddresses = new HashMap<>();

    public void setLivingRooms(Map<String, String> livingRooms) {
        livingRooms.forEach((path, address) -> {
            String room = path.split("\\.")[0];
            if (ADDRESS_STRING.equals(path.split("\\.")[2])) {
                universalSensorAddresses.put(room, Integer.valueOf(address));
            }
        });
    }

    public Integer getUniversalSensorAddress(UniversalSensor universalSensor) {
        return universalSensorAddresses.get(universalSensor.getRoom());
    }
}

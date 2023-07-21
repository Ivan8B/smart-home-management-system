package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModbusConfiguration {
    @Value("${modbus.tcpHost}")
    private String HOST;

    @Value("${modbus.tcpPort}")
    private Integer PORT;

    public String getHOST() {
        return HOST;
    }

    public Integer getPORT() {
        return PORT;
    }
}

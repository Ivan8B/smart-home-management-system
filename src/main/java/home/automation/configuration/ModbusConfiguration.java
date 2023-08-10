package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModbusConfiguration {
    @Value("${modbus.tcpHost}")
    private String host;

    @Value("${modbus.tcpPort}")
    private Integer port;

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }
}

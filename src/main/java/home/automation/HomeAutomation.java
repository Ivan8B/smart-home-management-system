package home.automation;

import home.automation.configuration.UniversalSensorsConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties(UniversalSensorsConfiguration.class)
public class HomeAutomation {
    public static void main(String[] args) {
        SpringApplication.run(HomeAutomation.class, args);
    }
}

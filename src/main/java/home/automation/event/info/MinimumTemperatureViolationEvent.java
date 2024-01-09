package home.automation.event.info;

import home.automation.enums.TemperatureSensor;
import org.springframework.context.ApplicationEvent;

public class MinimumTemperatureViolationEvent extends ApplicationEvent {
    TemperatureSensor sensor;
    public MinimumTemperatureViolationEvent(Object source, TemperatureSensor sensor) {
        super(source);
        this.sensor = sensor;
    }

    public TemperatureSensor getSensor() {
        return sensor;
    }
}

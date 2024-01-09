package home.automation.event.info;

import home.automation.enums.TemperatureSensor;
import org.springframework.context.ApplicationEvent;

public class MaximumTemperatureViolationEvent extends ApplicationEvent {
    TemperatureSensor sensor;
    public MaximumTemperatureViolationEvent(Object source, TemperatureSensor sensor) {
        super(source);
        this.sensor = sensor;
    }

    public TemperatureSensor getSensor() {
        return sensor;
    }
}

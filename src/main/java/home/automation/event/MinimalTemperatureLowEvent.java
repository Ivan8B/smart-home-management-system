package home.automation.event;

import home.automation.enums.TemperatureSensor;
import org.springframework.context.ApplicationEvent;

public class MinimalTemperatureLowEvent extends ApplicationEvent {
    TemperatureSensor sensor;
    public MinimalTemperatureLowEvent(Object source, TemperatureSensor sensor) {
        super(source);
        this.sensor = sensor;
    }

    public TemperatureSensor getSensor() {
        return sensor;
    }
}
package home.heating.event;

import home.heating.enums.TemperatureSensor;
import org.springframework.context.ApplicationEvent;

public class TemperatureSensorPollErrorEvent extends ApplicationEvent {
    TemperatureSensor sensor;

    public TemperatureSensorPollErrorEvent(Object source, TemperatureSensor sensor) {
        super(source);
        this.sensor = sensor;
    }

    public TemperatureSensor getSensor() {
        return sensor;
    }
}

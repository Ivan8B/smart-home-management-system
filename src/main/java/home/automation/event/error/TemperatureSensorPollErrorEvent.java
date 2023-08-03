package home.automation.event.error;

import home.automation.enums.TemperatureSensor;
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

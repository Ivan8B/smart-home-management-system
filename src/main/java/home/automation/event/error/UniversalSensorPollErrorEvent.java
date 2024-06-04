package home.automation.event.error;

import home.automation.enums.UniversalSensor;
import org.springframework.context.ApplicationEvent;

public class UniversalSensorPollErrorEvent extends ApplicationEvent {
    UniversalSensor sensor;

    public UniversalSensorPollErrorEvent(Object source, UniversalSensor sensor) {
        super(source);
        this.sensor = sensor;
    }

    public UniversalSensor getSensor() {
        return sensor;
    }
}

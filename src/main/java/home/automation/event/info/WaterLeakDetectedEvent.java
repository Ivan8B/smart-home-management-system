package home.automation.event.info;

import home.automation.enums.WaterLeakSensor;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.Set;

public class WaterLeakDetectedEvent extends ApplicationEvent {
    private final Set<WaterLeakSensor> triggeredSensors;

    public WaterLeakDetectedEvent(Object source, Set<WaterLeakSensor> triggeredSensors) {
        super(source);
        this.triggeredSensors = triggeredSensors;
    }

    public Set<WaterLeakSensor> getTriggeredSensors() {
        return Collections.unmodifiableSet(triggeredSensors);
    }
}

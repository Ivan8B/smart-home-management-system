package home.automation.event;

import home.automation.enums.FloorHeatingStatus;
import org.springframework.context.ApplicationEvent;

public class FloorHeatingStatusCalculatedEvent extends ApplicationEvent {
    FloorHeatingStatus status;

    public FloorHeatingStatusCalculatedEvent(Object source, FloorHeatingStatus status) {
        super(source);
        this.status = status;
    }

    public FloorHeatingStatus getStatus() {
        return status;
    }
}

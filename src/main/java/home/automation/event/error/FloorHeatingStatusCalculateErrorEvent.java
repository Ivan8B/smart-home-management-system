package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class FloorHeatingStatusCalculateErrorEvent extends ApplicationEvent {
    public FloorHeatingStatusCalculateErrorEvent(Object source) {
        super(source);
    }
}

package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class FloorHeatingErrorEvent extends ApplicationEvent {
    public FloorHeatingErrorEvent(Object source) {
        super(source);
    }
}

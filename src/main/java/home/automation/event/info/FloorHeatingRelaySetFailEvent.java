package home.automation.event.info;

import org.springframework.context.ApplicationEvent;

public class FloorHeatingRelaySetFailEvent extends ApplicationEvent {
    public FloorHeatingRelaySetFailEvent(Object source) {
        super(source);
    }
}

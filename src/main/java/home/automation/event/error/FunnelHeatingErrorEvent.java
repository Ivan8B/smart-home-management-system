package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class FunnelHeatingErrorEvent extends ApplicationEvent {
    public FunnelHeatingErrorEvent(Object source) {
        super(source);
    }
}

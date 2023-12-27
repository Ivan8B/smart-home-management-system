package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class HeatRequestErrorEvent extends ApplicationEvent {
    public HeatRequestErrorEvent(Object source) {
        super(source);
    }
}

package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class HeatingPumpsErrorEvent extends ApplicationEvent {
    public HeatingPumpsErrorEvent(Object source) {
        super(source);
    }
}

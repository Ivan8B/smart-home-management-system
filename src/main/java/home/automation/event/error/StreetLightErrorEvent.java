package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class StreetLightErrorEvent extends ApplicationEvent {
    public StreetLightErrorEvent(Object source) {
        super(source);
    }
}

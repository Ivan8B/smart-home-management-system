package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class StreetLightRelaySetFailEvent extends ApplicationEvent {
    public StreetLightRelaySetFailEvent(Object source) {
        super(source);
    }
}

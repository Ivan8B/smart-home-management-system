package home.automation.event;

import org.springframework.context.ApplicationEvent;

public class GasBoilerRelaySetFailEvent extends ApplicationEvent {
    public GasBoilerRelaySetFailEvent(Object source) {
        super(source);
    }
}

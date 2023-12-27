package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class GasBoilerErrorEvent extends ApplicationEvent {
    public GasBoilerErrorEvent(Object source) {
        super(source);
    }
}

package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class ElectricBoilerErrorEvent extends ApplicationEvent {
    public ElectricBoilerErrorEvent(Object source) {
        super(source);
    }
}

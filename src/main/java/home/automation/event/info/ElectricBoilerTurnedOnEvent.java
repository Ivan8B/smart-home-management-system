package home.automation.event.info;

import org.springframework.context.ApplicationEvent;

public class ElectricBoilerTurnedOnEvent extends ApplicationEvent {
    public ElectricBoilerTurnedOnEvent(Object source) {
        super(source);
    }
}

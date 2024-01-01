package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class CityPowerInputErrorEvent extends ApplicationEvent {
    public CityPowerInputErrorEvent(Object source) {
        super(source);
    }
}

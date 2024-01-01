package home.automation.event.info;

import org.springframework.context.ApplicationEvent;

public class CityPowerInputNoPowerEvent extends ApplicationEvent {
    public CityPowerInputNoPowerEvent(Object source) {
        super(source);
    }
}

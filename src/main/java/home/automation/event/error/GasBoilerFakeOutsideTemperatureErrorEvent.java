package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class GasBoilerFakeOutsideTemperatureErrorEvent extends ApplicationEvent {
    public GasBoilerFakeOutsideTemperatureErrorEvent(Object source) {
        super(source);
    }
}

package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class WaterLeakErrorEvent extends ApplicationEvent {
    public WaterLeakErrorEvent(Object source) {
        super(source);
    }
}
package home.automation.event.info;

import home.automation.enums.HeatRequestStatus;
import org.springframework.context.ApplicationEvent;

public class HeatRequestCalculatedEvent extends ApplicationEvent {
    HeatRequestStatus status;

    public HeatRequestCalculatedEvent(Object source, HeatRequestStatus status) {
        super(source);
        this.status = status;
    }

    public HeatRequestStatus getStatus() {
        return status;
    }
}

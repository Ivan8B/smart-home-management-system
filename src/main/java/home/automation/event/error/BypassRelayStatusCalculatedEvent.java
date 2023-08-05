package home.automation.event.error;

import home.automation.enums.BypassRelayStatus;
import org.springframework.context.ApplicationEvent;

public class BypassRelayStatusCalculatedEvent extends ApplicationEvent {
    BypassRelayStatus status;

    public BypassRelayStatusCalculatedEvent(Object source, BypassRelayStatus status) {
        super(source);
        this.status = status;
    }

    public BypassRelayStatus getStatus() {
        return status;
    }
}
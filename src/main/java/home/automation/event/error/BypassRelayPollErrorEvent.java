package home.automation.event.error;

import org.springframework.context.ApplicationEvent;

public class BypassRelayPollErrorEvent extends ApplicationEvent {
    public BypassRelayPollErrorEvent(Object source) {
        super(source);
    }
}

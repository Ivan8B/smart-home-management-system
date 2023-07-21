package home.automation.event;

import org.springframework.context.ApplicationEvent;

public class BypassRelayPollErrorEvent extends ApplicationEvent {
    public BypassRelayPollErrorEvent(Object source) {
        super(source);
    }
}

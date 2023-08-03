package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BypassRelayConfiguration {
    @Value("${bypass.pollCountInPeriod}")
    private Integer POLL_COUNT_IN_PERIOD;

    @Value("${bypass.openThreshold}")
    private Integer OPEN_THRESHOLD;

    @Value("${bypass.relay.address}")
    private Integer ADDRESS;

    @Value("${bypass.relay.discreteInput}")
    private Integer DISCRETE_INPUT;

    public Integer getPollCountInPeriod() {
        return POLL_COUNT_IN_PERIOD;
    }

    public Integer getOpenThreshold() {
        return OPEN_THRESHOLD;
    }

    public Integer getAddress() {
        return ADDRESS;
    }

    public Integer getDiscreteInput() {
        return DISCRETE_INPUT;
    }

}

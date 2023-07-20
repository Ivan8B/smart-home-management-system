package home.heating.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BypassRelayConfiguration {
    @Value("${bypassRelay.pollCountInPeriod}")
    private Integer POLL_COUNT_IN_PERIOD;

    @Value("${bypassRelay.openThreshold}")
    private Integer OPEN_THRESHOLD;

    @Value("${bypassRelay.address}")
    private Integer ADDRESS;

    @Value("${bypassRelay.discreteInput}")
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

package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BypassRelayConfiguration {
    @Value("${bypass.pollCountInPeriod}")
    private Integer pollCountInPeriod;

    @Value("${bypass.openThreshold}")
    private Integer openThreshold;

    @Value("${bypass.relay.address}")
    private Integer address;

    @Value("${bypass.relay.discreteInput}")
    private Integer discreteInput;

    public Integer getPollCountInPeriod() {
        return pollCountInPeriod;
    }

    public Integer getOpenThreshold() {
        return openThreshold;
    }

    public Integer getAddress() {
        return address;
    }

    public Integer getDiscreteInput() {
        return discreteInput;
    }

}

package home.automation.service;

import home.automation.enums.GasBoilerRelayStatus;

public interface GasBoilerService {
    String manualTurnOn();

    String manualTurnOff();

    GasBoilerRelayStatus getStatus();

    String getFormattedStatus();
}

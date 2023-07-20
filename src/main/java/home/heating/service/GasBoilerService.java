package home.heating.service;

import home.heating.enums.GasBoilerRelayStatus;

public interface GasBoilerService {
    String manualTurnOn();

    String manualTurnOff();

    GasBoilerRelayStatus getStatus();

    String getFormattedStatus();
}

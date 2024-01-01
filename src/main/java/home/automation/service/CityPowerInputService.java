package home.automation.service;

import home.automation.enums.CityPowerInputStatus;

public interface CityPowerInputService {
    CityPowerInputStatus getStatus();

    String getFormattedStatus();
}

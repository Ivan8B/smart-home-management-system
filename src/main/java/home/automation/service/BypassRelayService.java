package home.automation.service;

import home.automation.enums.BypassRelayStatus;

public interface BypassRelayService {
    BypassRelayStatus getBypassRelayCalculatedStatus();
}

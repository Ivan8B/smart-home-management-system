package home.heating.service;

import home.heating.enums.BypassRelayStatus;

public interface BypassRelayService {
    BypassRelayStatus getBypassRelayCalculatedStatus();
}

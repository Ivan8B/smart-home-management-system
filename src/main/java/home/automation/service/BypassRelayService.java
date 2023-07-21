package home.automation.service;

import home.automation.enums.BypassRelayStatus;

public interface BypassRelayService {
    /**
     * Получить последний расчитанный статус реле байпаса
     * @return статус реле байпаса
     */
    BypassRelayStatus getBypassRelayCalculatedStatus();
}

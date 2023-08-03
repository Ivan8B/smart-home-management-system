package home.automation.service;

import home.automation.enums.BypassRelayStatus;

public interface BypassRelayService {
    /**
     * Получить последний расчитанный статус реле байпаса
     * @return статус реле байпаса
     */
    BypassRelayStatus getStatus();

    /**
     * Получение последний расчитанный статус реле байпаса текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

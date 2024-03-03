package home.automation.service;

import home.automation.enums.StreetLightStatus;

public interface StreetLightService {
    /**
     * Получение статуса уличного освещения
     *
     * @return статус уличного освещения
     */
    StreetLightStatus getStatus();

    /**
     * Получение статуса уличного освещения текстом
     *
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

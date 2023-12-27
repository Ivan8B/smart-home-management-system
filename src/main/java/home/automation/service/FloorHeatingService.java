package home.automation.service;

import home.automation.enums.FloorHeatingStatus;

public interface FloorHeatingService {
    /**
     * Получить последний расчитанный статус запроса тепла в пол
     *
     * @return статус запроса тепла в пол
     */
    FloorHeatingStatus getStatus();

    /**
     * Получение статуса запроса тепла в пол текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

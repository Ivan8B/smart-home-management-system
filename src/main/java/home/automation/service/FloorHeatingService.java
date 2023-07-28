package home.automation.service;

import home.automation.enums.FloorHeatingStatus;

public interface FloorHeatingService {
    /**
     * Получить последний расчитанный статус запроса на полом
     *
     * @return статус запроса тепла в пол
     */
    FloorHeatingStatus getFloorHeatingStatus();
}

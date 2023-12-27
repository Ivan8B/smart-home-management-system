package home.automation.service;

import home.automation.enums.HeatRequestStatus;

public interface HeatRequestService {
    /**
     * Получить последний расчитанный статус запроса на тепло
     * @return статус реле байпаса
     */
    HeatRequestStatus getStatus();

    /**
     * Получение последний расчитанный статус запроса на тепло текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

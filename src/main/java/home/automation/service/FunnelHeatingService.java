package home.automation.service;

import home.automation.enums.FunnelHeatingStatus;

public interface FunnelHeatingService {
    /**
     * Получение статуса обогрева воронок
     *
     * @return статус обогрева воронок
     */
    FunnelHeatingStatus getStatus();

    /**
     * Получение статуса обогрева воронок текстом
     *
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

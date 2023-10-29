package home.automation.service;

import home.automation.enums.GasBoilerStatus;

public interface GasBoilerService {
    /**
     * Получение статуса газового котла (рассчитывается по росту температуры подачи)
     * @return статус газового котла
     */
    GasBoilerStatus getStatus();

    /**
     * Получение статуса газового котла текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();

    /**
     * Полуение аналитики по работе котла за прошедшие сутки
     * @return сообщение для бота
     */
    String getFormattedStatusForLastDay();
}

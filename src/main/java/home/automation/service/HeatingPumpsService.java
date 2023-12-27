package home.automation.service;

import home.automation.enums.HeatingPumpsStatus;

public interface HeatingPumpsService {
    /**
     * Получить статус работы насосов отопления
     *
     * @return статус работы насосов отопления
     */
    HeatingPumpsStatus getStatus();

    /**
     * Получение статуса насосов отопления текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

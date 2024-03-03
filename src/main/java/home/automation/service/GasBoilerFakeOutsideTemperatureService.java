package home.automation.service;

import home.automation.enums.GasBoilerFakeOutsideTemperatureStatus;

public interface GasBoilerFakeOutsideTemperatureService {
    /**
     * Получение статуса обманки газового котла
     *
     * @return статус обманки газового котла
     */
    GasBoilerFakeOutsideTemperatureStatus getStatus();

    /**
     * Получение статуса обманки газового котла текстом
     *
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

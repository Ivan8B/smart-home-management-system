package home.automation.service;

import home.automation.enums.ElectricBoilerStatus;

public interface ElectricBoilerService {
    /**
     * Получение статуса электрического котла
     *
     * @return статус электрического котла
     */
    ElectricBoilerStatus getStatus();

    /**
     * Получение статуса электрического котла текстом
     *
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

package home.automation.service;

import home.automation.enums.GasBoilerStatus;

public interface GasBoilerService {
    /**
     * Ручное включение газового котла (автоматически отключится при необходимости, если реле байпаса опрашивается)
     * @return сообщение для бота о статусе операции
     */
    String manualTurnOn();

    /**
     * Ручное отключение газового котла (автоматически включится при необходимости, если реле байпаса опрашивается)
     * @return сообщение для бота о статусе операции
     */
    String manualTurnOff();

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
}

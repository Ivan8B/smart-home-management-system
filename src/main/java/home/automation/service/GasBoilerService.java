package home.automation.service;

import home.automation.enums.GasBoilerRelayStatus;

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
     * Получение статуса газового котла (а фактически - реле термостата)
     * @return статус реле газового котла
     */
    GasBoilerRelayStatus getStatus();

    /**
     * Получение статуса газового котла текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

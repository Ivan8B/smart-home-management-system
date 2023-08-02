package home.automation.service;

public interface StreetLightService {
    /**
     * Получение статуса уличного освещения текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

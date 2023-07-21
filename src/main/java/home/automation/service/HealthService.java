package home.automation.service;

public interface HealthService {
    /**
     * Получение статуса селфмониторинга текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

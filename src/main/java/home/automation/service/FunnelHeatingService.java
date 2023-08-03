package home.automation.service;

public interface FunnelHeatingService {
    /**
     * Получение статуса обогрева воронок текстом
     * @return сообщение для бота
     */
    String getFormattedStatus();
}

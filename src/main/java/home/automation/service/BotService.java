package home.automation.service;

public interface BotService {
    /**
     * Отправить сообщение всем пользователям
     * @param text текст сообщения
     */
    void notify(String text);
}

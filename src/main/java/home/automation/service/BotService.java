package home.automation.service;

public interface BotService {
    /**
     * Отпрваить сообщение всем пользвоателям
     * @param text текст сообщения
     */
    void notify(String text);
}

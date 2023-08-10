package home.automation.configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelegramBotConfiguration {
    @Value("${bot.name}")
    private String botName;

    @Value("${bot.token}")
    private String token;

    @Value("${bot.validUserIds}")
    private List<Long> validUserIds;

    @Value("${bot.chatIds}")
    private List<Long> chatIds;

    public String getBotName() {
        return botName;
    }

    public String getToken() {
        return token;
    }

    public List<Long> getValidUserIds() {
        return validUserIds;
    }

    public List<Long> getChatIds() {
        return chatIds;
    }
}
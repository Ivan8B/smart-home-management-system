package home.heating.configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelegramBotConfiguration {
    @Value("${bot.name}")
    private String BOT_NAME;

    @Value("${bot.token}")
    private String TOKEN;

    @Value("${bot.validUserIds}")
    private List<Long> VALID_USER_IDS;

    @Value("${bot.chatIds}")
    private List<Long> CHAT_IDS;

    public String getBotName() {
        return BOT_NAME;
    }

    public String getToken() {
        return TOKEN;
    }

    public List<Long> getValidUserIds() {
        return VALID_USER_IDS;
    }

    public List<Long> getChatIds() {
        return CHAT_IDS;
    }
}
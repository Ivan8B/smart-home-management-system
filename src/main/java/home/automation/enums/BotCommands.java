package home.automation.enums;

public enum BotCommands {
    GET_STATUS("/get_status");

    private final String telegramCommand;

    BotCommands(String telegramCommand) {
        this.telegramCommand = telegramCommand;
    }

    public String getTelegramCommand() {
        return telegramCommand;
    }
}

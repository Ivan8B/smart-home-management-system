package home.automation.enums;

public enum BotCommands {
    GET_STATUS("/get_status"),
    OPEN_WATER("/open_water"),
    CLOSE_FILTERS("/close_filters");

    private final String telegramCommand;

    BotCommands(String telegramCommand) {
        this.telegramCommand = telegramCommand;
    }

    public String getTelegramCommand() {
        return telegramCommand;
    }
}

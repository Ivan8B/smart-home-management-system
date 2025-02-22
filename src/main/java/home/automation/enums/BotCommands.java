package home.automation.enums;

public enum BotCommands {
    GET_STATUS("/get_status"),
    CALIBRATE_FLOOR("/calibrate_floor");

    private final String telegramCommand;

    BotCommands(String telegramCommand) {
        this.telegramCommand = telegramCommand;
    }

    public String getTelegramCommand() {
        return telegramCommand;
    }
}

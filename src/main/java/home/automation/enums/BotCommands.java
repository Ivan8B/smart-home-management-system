package home.automation.enums;

public enum BotCommands {
    GET_STATUS("/get_status"),

    GAS_BOILER_ON("/gas_boiler_on"),

    GAS_BOILER_OFF("/gas_boiler_off");

    private final String telegramCommand;

    BotCommands(String telegramCommand) {
        this.telegramCommand = telegramCommand;
    }

    public String getTelegramCommand() {
        return telegramCommand;
    }
}

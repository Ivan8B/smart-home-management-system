package home.automation.enums;

public enum BotCommands {
    GET_CURRENT_TEMPERATURES("/current_temperatures"),

    GAS_BOILER_ON("/gas_boiler_on"),

    GAS_BOILER_OFF("/gas_boiler_off"),

    GET_GAS_BOILER_STATUS("/gas_boiler_status"),

    GET_SELF_MONITORING_STATUS("/self_monitoring_status");

    private final String telegramCommand;

    BotCommands(String telegramCommand) {
        this.telegramCommand = telegramCommand;
    }

    public String getTelegramCommand() {
        return telegramCommand;
    }
}

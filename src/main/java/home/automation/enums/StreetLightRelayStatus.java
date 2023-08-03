package home.automation.enums;

public enum StreetLightRelayStatus {
    TURNED_ON("статус уличного освещения - включено"),

    TURNED_OFF("статус уличного освещения - отключено"),

    ERROR("статус уличного освещения - ошибка");

    private final String template;

    StreetLightRelayStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

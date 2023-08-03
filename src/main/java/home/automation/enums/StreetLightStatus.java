package home.automation.enums;

public enum StreetLightStatus {
    INIT("статус уличного освещения - неизвестен!"),

    TURNED_ON("статус уличного освещения - включено"),

    TURNED_OFF("статус уличного освещения - отключено"),

    ERROR("статус уличного освещения - ошибка!");

    private final String template;

    StreetLightStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

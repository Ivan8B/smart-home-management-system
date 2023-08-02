package home.automation.enums;

public enum StreetLightRelayStatus {
    TURNED_ON("Освещение включено"),

    TURNED_OFF("Освещение отключено"),

    ERROR("Ошибка работы с реле освещения");

    private final String template;

    StreetLightRelayStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

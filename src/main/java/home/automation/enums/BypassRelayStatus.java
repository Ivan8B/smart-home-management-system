package home.automation.enums;

public enum BypassRelayStatus {
    INIT("статус реле байпаса радиаторов - неизвестный статус!"),

    OPEN("статус реле байпаса радиаторов - есть запрос на тепло"),

    CLOSED("статус реле байпаса радиаторов - нет запроса на тепло"),

    ERROR("статус реле байпаса радиаторов - ошибка!");

    private final String template;

    BypassRelayStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

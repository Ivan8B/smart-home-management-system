package home.automation.enums;

public enum BypassRelayStatus {
    INIT("статус реле байпаса радиаторов - неизвестный статус!", -2),

    OPEN("статус реле байпаса радиаторов - есть запрос на тепло", 1),

    CLOSED("статус реле байпаса радиаторов - нет запроса на тепло", 0),

    ERROR("статус реле байпаса радиаторов - ошибка!", -1);

    private final String template;

    private final Integer numericStatus;

    BypassRelayStatus(String template, Integer numericStatus) {
        this.template = template;
        this.numericStatus = numericStatus;
    }

    public String getTemplate() {
        return template;
    }

    public Integer getNumericStatus() {
        return numericStatus;
    }
}

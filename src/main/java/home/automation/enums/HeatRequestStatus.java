package home.automation.enums;

public enum HeatRequestStatus {
    NEED_HEAT("статус запроса на тепло в дом - есть запрос на тепло", 1),

    NO_NEED_HEAT("статус запроса на тепло в дом - нет запроса на тепло", 0),

    ERROR("статус запроса на тепло в дом - ошибка!", -1);

    private final String template;

    private final Integer numericStatus;

    HeatRequestStatus(String template, Integer numericStatus) {
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

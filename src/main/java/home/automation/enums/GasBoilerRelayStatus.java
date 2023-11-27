package home.automation.enums;

public enum GasBoilerRelayStatus {
    NEED_HEAT("статус реле газового котла - есть запрос на тепло", 1),

    NO_NEED_HEAT("статус реле газового котла - нет запроса на тепло", 0),

    ERROR("статус реле газового котла - ошибка!", -1);

    private final String template;

    private final Integer numericStatus;

    GasBoilerRelayStatus(String template, Integer numericStatus) {
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

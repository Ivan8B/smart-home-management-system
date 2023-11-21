package home.automation.enums;

public enum GasBoilerStatus {

    INIT("статус газового котла - неизвестный статус!", -2),

    ERROR("статус газового котла - ошибка расчета статуса!", -1),

    IDLE("статус газового котла - не работает на отопление", 0),

    WORKS("статус газового котла - работает на отопление", 1);

    private final String template;

    private final Integer numericStatus;

    GasBoilerStatus(String template, Integer numericStatus) {
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

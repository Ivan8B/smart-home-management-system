package home.automation.enums;

public enum GasBoilerStatus {

    INIT("статус газового котла - неизвестный статус"),

    WORKS("статус газового котла - работает на отопление"),

    IDLE("статус газового котла - не работает на отопление"),

    ERROR("статус газового котла - ошибка расчета статуса");

    private final String template;

    GasBoilerStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

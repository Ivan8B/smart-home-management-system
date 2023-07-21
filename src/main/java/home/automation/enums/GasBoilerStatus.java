package home.automation.enums;

public enum GasBoilerStatus {

    INIT("Газовый котел в неизвестном статусе"),

    WORKS("Газовый котел работает на отопление"),

    IDLE("Газовый котел не работает на отопление"),

    ERROR("Ошибка расчета статуса газового котла");

    private final String template;

    GasBoilerStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

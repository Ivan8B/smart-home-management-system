package home.automation.enums;

public enum CityPowerInputStatus {
    POWER_ON("статус напряжения на входе ИБП - есть"),

    POWER_OFF("статус напряжения на входе ИБП - нет!"),

    ERROR("статус напряжения на входе ИБП - ошибка!");

    private final String template;

    CityPowerInputStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

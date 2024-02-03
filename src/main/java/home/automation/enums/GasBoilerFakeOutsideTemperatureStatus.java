package home.automation.enums;

public enum GasBoilerFakeOutsideTemperatureStatus {
    TURNED_ON_1_DEGREE("статус обманки газового котла - включена, +1°"),

    TURNED_ON_MINUS_20_DEGREE("статус обманки газового котла - включена, -20°"),

    TURNED_OFF("статус обманки газового котла - отключена"),

    ERROR("статус обманки газового котла - ошибка!");

    private final String template;

    GasBoilerFakeOutsideTemperatureStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

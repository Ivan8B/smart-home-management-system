package home.automation.enums;

public enum ElectricBoilerStatus {
    TURNED_ON("статус электрического котла - включен"),

    TURNED_OFF("статус электрического котла - отключен"),

    ERROR("статус электрического котла - ошибка!");

    private final String template;

    ElectricBoilerStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

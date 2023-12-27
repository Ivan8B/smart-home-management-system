package home.automation.enums;

public enum HeatingPumpsStatus {
    TURNED_ON("статус насосов отопления - включены"),

    TURNED_OFF("статус насосов отопления - отключены"),

    ERROR("статус насосов отопления - ошибка!");

    private final String template;

    HeatingPumpsStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

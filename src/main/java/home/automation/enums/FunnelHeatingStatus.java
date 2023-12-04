package home.automation.enums;

public enum FunnelHeatingStatus {
    TURNED_ON("статус обогрева воронок - включен"),

    TURNED_OFF("статус обогрева воронок - отключен"),

    ERROR("статус обогрева воронок - ошибка!");

    private final String template;

    FunnelHeatingStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

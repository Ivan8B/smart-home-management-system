package home.automation.enums;

public enum FunnelHeatingStatus {
    INIT("Обогрев воронок в неизвестном статусе"),

    TURNED_ON("Обогрев воронок включен"),

    TURNED_OFF("Обогрев воронок отключен"),

    ERROR("Ошибка работы обогрева воронок");

    private final String template;

    FunnelHeatingStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

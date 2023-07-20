package home.heating.enums;

public enum SelfMonitoringStatus {
    OK("Все хорошо"),

    MINOR_PROBLEMS("Небольшие проблемы"),

    EMERGENCY("Аварийная ситуация!");

    private final String template;

    SelfMonitoringStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

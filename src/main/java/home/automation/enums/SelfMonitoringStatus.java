package home.automation.enums;

public enum SelfMonitoringStatus {
    OK("все хорошо"),

    MINOR_PROBLEMS("небольшие проблемы"),

    EMERGENCY("аварийная ситуация!");

    private final String template;

    SelfMonitoringStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

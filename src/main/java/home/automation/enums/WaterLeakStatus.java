package home.automation.enums;

public enum WaterLeakStatus {
    OK("статус протечки - норма"),

    LEAK("статус протечки - авария, вода перекрыта!"),

    ERROR("статус протечки - ошибка!");

    private final String template;

    WaterLeakStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

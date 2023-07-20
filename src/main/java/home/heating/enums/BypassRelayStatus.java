package home.heating.enums;

public enum BypassRelayStatus {
    INIT("Запрос тепла в радиаторы в неизвестном статусе"),

    OPEN("Запрос тепла в радиаторы есть"),

    CLOSED("Запроса тепла в радиаторы нет"),

    ERROR("Ошибка опроса реле");

    private final String template;

    BypassRelayStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

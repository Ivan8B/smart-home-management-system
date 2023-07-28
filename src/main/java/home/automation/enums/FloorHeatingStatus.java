package home.automation.enums;

public enum FloorHeatingStatus {
    INIT("Запрос тепла в теплые полы в неизвестном статусе"),

    NEED_HEAT("Запрос тепла в теплые полы есть"),

    NO_NEED_HEAT("Запроса тепла в теплые полы нет"),

    ERROR("Ошибка опроса температурных датчиков");

    private final String template;

    FloorHeatingStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

package home.automation.enums;

public enum FloorHeatingStatus {
    INIT("статус запроса тепла в теплые полы - в неизвестном статусе"),

    NEED_HEAT("статус запроса тепла в теплые полы - есть запрос"),

    NO_NEED_HEAT("статус запроса тепла в теплые полы - нет запроса"),

    ERROR("статус запроса тепла в теплые полы - ошибка опроса датчиков");

    private final String template;

    FloorHeatingStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

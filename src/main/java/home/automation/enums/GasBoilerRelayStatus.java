package home.automation.enums;

public enum GasBoilerRelayStatus {

    INIT("статус реле газового котла - неизвестный статус!"),

    NEED_HEAT("статус реле газового котла - есть запрос на тепло"),

    NO_NEED_HEAT("статус реле газового котла - нет запроса на тепло"),

    ERROR("статус реле газового котла - ошибка!");

    private final String template;

    GasBoilerRelayStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

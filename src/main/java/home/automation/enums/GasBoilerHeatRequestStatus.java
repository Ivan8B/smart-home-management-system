package home.automation.enums;

public enum GasBoilerHeatRequestStatus {

    INIT("запрос на работу газового котла - неизвестный статус!"),

    NEED_HEAT("есть запрос на работу газового котла"),

    NO_NEED_HEAT("нет запроса на работу газового котла");

    private final String template;

    GasBoilerHeatRequestStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

package home.heating.enums;

public enum GasBoilerRelayStatus {

    NEED_HEAT("Запрос на включение газового котла"),

    NO_NEED_HEAT("Нет запроса на работу газового котла"),

    ERROR("Ошибка работы с реле газового котла");

    private final String template;

    GasBoilerRelayStatus(String template) {
        this.template = template;
    }

    public String getTemplate() {
        return template;
    }
}

package home.heating.enums;

public enum TemperatureSensor {
    BOILER_ROOM_TEMPERATURE
        (
            0,
            "Температура в котельной",
            true,
            15F
        ),

    WATER_DIRECT_GAS_BOILER_TEMPERATURE
        (
            1,
            "Подача из газового котла"
        ),

    WATER_RETURN_GAS_BOILER_TEMPERATURE
        (2,
            "Обратка в газовый котел "
        ),

    WATER_DIRECT_FLOOR_TEMPERATURE
        (3,
            "Подача в теплые полы     "
        ),

    WATER_RETURN_FLOOR_TEMPERATURE
        (4,
            "Обратка из теплых полов"
        ),

    OUTSIDE_TEMPERATURE
        (5,
            "Температура на улице      "
        );

    private final Integer registerId;

    private final String template;

    private final boolean isCritical;

    private final Float minimalTemperature;

    TemperatureSensor(Integer registerId, String template) {
        this.registerId = registerId;
        this.template = template;
        this.isCritical = false;
        this.minimalTemperature = null;
    }

    TemperatureSensor(Integer registerId, String template, Boolean isCritical, Float minimalTemperature) {
        this.registerId = registerId;
        this.template = template;
        this.isCritical = isCritical;
        this.minimalTemperature = minimalTemperature;
    }

    public Integer getRegisterId() {
        return registerId;
    }

    public String getTemplate() {
        return template;
    }

    public boolean isCritical() {
        return isCritical;
    }

    public Float getMinimalTemperature() {
        return minimalTemperature;
    }
}

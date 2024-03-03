package home.automation.enums;

import static home.automation.configuration.TemperatureSensorsBoardsConfiguration.FIRST_BOARD_NAME;

public enum TemperatureSensor {
    BOILER_ROOM_TEMPERATURE
            (
                    0,
                    FIRST_BOARD_NAME,
                    "температура в котельной",
                    true,
                    12F,
                    null
            ),

    WATER_DIRECT_GAS_BOILER_TEMPERATURE
            (
                    1,
                    FIRST_BOARD_NAME,
                    "подача из газового котла",
                    true,
                    null,
                    null
            ),

    WATER_RETURN_GAS_BOILER_TEMPERATURE
            (
                    2,
                    FIRST_BOARD_NAME,
                    "обратка в газовый котел",
                    true,
                    null,
                    null
            ),

    OUTSIDE_TEMPERATURE
            (
                    3,
                    FIRST_BOARD_NAME,
                    "температура на улице",
                    true,
                    null,
                    null
            ),

    WATER_DIRECT_FLOOR_TEMPERATURE_BEFORE_MIXING
            (
                    4,
                    FIRST_BOARD_NAME,
                    "подача в теплые полы до подмеса",
                    true,
                    null,
                    null
            ),

    WATER_DIRECT_FLOOR_TEMPERATURE_AFTER_MIXING
            (
                    5,
                    FIRST_BOARD_NAME,
                    "подача в теплые полы после подмеса",
                    true,
                    null,
                    45f
            ),

    WATER_RETURN_FLOOR_TEMPERATURE
            (
                    6,
                    FIRST_BOARD_NAME,
                    "обратка из теплых полов",
                    true,
                    null,
                    null
            ),

    CHILD_BATHROOM_TEMPERATURE
            (
                    7,
                    FIRST_BOARD_NAME,
                    "температура в детском санузле",
                    true,
                    null,
                    null
            );

    private final Integer registerId;

    private final String boardName;

    private final String template;

    private final boolean isCritical;

    private final Float minimumTemperature;

    private final Float maximumTemperature;

    TemperatureSensor(Integer registerId,
                      String boardName,
                      String template,
                      Boolean isCritical,
                      Float minimumTemperature,
                      Float maximumTemperature) {
        this.registerId = registerId;
        this.boardName = boardName;
        this.template = template;
        this.isCritical = isCritical;
        this.minimumTemperature = minimumTemperature;
        this.maximumTemperature = maximumTemperature;
    }

    public Integer getRegisterId() {
        return registerId;
    }

    public String getBoardName() {
        return boardName;
    }

    public String getTemplate() {
        return template;
    }

    public boolean isCritical() {
        return isCritical;
    }

    public Float getMinimumTemperature() {
        return minimumTemperature;
    }

    public Float getMaximumTemperature() {
        return maximumTemperature;
    }
}

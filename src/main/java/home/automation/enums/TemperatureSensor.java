package home.automation.enums;

import static home.automation.configuration.TemperatureSensorsBoardsConfiguration.FIRST_BOARD_NAME;

public enum TemperatureSensor {
    BOILER_ROOM_TEMPERATURE
        (
            0,
            FIRST_BOARD_NAME,
            "температура в котельной",
            true,
            15F
        ),

    WATER_DIRECT_GAS_BOILER_TEMPERATURE
        (
            1,
            FIRST_BOARD_NAME,
            "подача из газового котла",
            true
        ),

    WATER_RETURN_GAS_BOILER_TEMPERATURE
        (
            2,
            FIRST_BOARD_NAME,
            "обратка в газовый котел"
        ),

    OUTSIDE_TEMPERATURE
        (
            3,
            FIRST_BOARD_NAME,
            "температура на улице",
            true
        ),

    WATER_DIRECT_FLOOR_TEMPERATURE_BEFORE_MIXING
        (
            4,
            FIRST_BOARD_NAME,
            "подача в теплые полы до подмеса",
            true
        ),

    WATER_DIRECT_FLOOR_TEMPERATURE_AFTER_MIXING
        (
            5,
            FIRST_BOARD_NAME,
            "подача в теплые полы после подмеса",
            true
        ),

    WATER_RETURN_FLOOR_TEMPERATURE
        (
            6,
            FIRST_BOARD_NAME,
            "обратка из теплых полов",
            true
        ),

    CHILD_BATHROOM_TEMPERATURE
        (
            7,
            FIRST_BOARD_NAME,
            "температура в детском санузле"
        );

    private final Integer registerId;

    private final String boardName;

    private final String template;

    private final boolean isCritical;

    private final Float minimalTemperature;

    TemperatureSensor(Integer registerId, String boardName, String template) {
        this.registerId = registerId;
        this.boardName = boardName;
        this.template = template;
        this.isCritical = false;
        this.minimalTemperature = null;
    }

    TemperatureSensor(Integer registerId, String boardName, String template, Boolean isCritical) {
        this.registerId = registerId;
        this.boardName = boardName;
        this.template = template;
        this.isCritical = isCritical;
        this.minimalTemperature = null;
    }

    TemperatureSensor(Integer registerId, String boardName, String template, Boolean isCritical, Float minimalTemperature) {
        this.registerId = registerId;
        this.boardName = boardName;
        this.template = template;
        this.isCritical = isCritical;
        this.minimalTemperature = minimalTemperature;
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

    public Float getMinimalTemperature() {
        return minimalTemperature;
    }
}

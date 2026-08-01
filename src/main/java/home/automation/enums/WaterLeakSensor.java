package home.automation.enums;

/**
 * Датчики протечки, подключенные к N4DIG08 (режим 8 Input).
 * Регистры идут подряд начиная с waterLeak.sensors.registerStart.
 */
public enum WaterLeakSensor {
    SENSOR_1(0, "датчик протечки -  котельная"),
    SENSOR_2(1, "датчик протечки - постирочная"),
    SENSOR_3(2, "датчик протечки - кухня"),
    SENSOR_4(3, "датчик протечки - санузел в спальне"),
    SENSOR_5(4, "датчик протечки - санузел в гостиной"),
    SENSOR_6(5, "датчик протечки - санузел у входа");

    private final int registerOffset;
    private final String template;

    WaterLeakSensor(int registerOffset, String template) {
        this.registerOffset = registerOffset;
        this.template = template;
    }

    public int getRegisterOffset() {
        return registerOffset;
    }

    public String getTemplate() {
        return template;
    }
}

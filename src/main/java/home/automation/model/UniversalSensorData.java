package home.automation.model;

public class UniversalSensorData {
    private final Float temperature;
    private final Integer humidityPercent;
    private final Integer co2ppm;

    public UniversalSensorData(Float temperature, Integer humidityPercent, Integer co2ppm) {
        this.temperature = temperature;
        this.humidityPercent = humidityPercent;
        this.co2ppm = co2ppm;
    }

    public Float getTemperature() {
        return temperature;
    }

    public Integer getHumidityPercent() {
        return humidityPercent;
    }

    public Integer getCO2ppm() {
        return co2ppm;
    }
}

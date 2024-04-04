package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GasBoilerConfiguration {
    @Value("${gasBoiler.relay.address}")
    private Integer address;

    @Value("${gasBoiler.relay.coil}")
    private Integer coil;

    @Value("${gasBoiler.temperature.turnOnMinDelta}")
    private Float turnOnMinDelta;

    @Value("${gasBoiler.temperature.turnOffDirectDelta}")
    private Float turnOffDirectDelta;

    @Value("${gasBoiler.temperature.direct.min}")
    private Float temperatureDirectMin;

    @Value("${gasBoiler.temperature.direct.max}")
    private Float temperatureDirectMax;

    @Value("${gasBoiler.temperature.direct.blockDelta}")
    private Integer temperatureDirectBlockDelta;

    @Value("${gasBoiler.temperature.return.onMaxCurvePoint}")
    private Float temperatureReturnOnMaxCurvePoint;

    @Value("${gasBoiler.temperature.return.onMinCurvePoint}")
    private Float temperatureReturnOnMinCurvePoint;

    @Value("${gasBoiler.temperature.return.min}")
    private Float temperatureReturnMin;

    @Value("${gasBoiler.temperature.weatherCurve.min}")
    private Float temperatureWeatherCurveMin;

    @Value("${gasBoiler.temperature.weatherCurve.max}")
    private Float temperatureWeatherCurveMax;

    @Value("${gasBoiler.waterFlow}")
    private Float waterFlow;

    public Integer getAddress() {
        return address;
    }

    public Integer getCoil() {
        return coil;
    }

    public Float getTurnOnMinDelta() {
        return turnOnMinDelta;
    }

    public Float getTurnOffDirectDelta() {
        return turnOffDirectDelta;
    }

    public Float getTemperatureDirectMin() {
        return temperatureDirectMin;
    }

    public Float getTemperatureDirectMax() {
        return temperatureDirectMax;
    }

    public Integer getTemperatureDirectBlockDelta() {
        return temperatureDirectBlockDelta;
    }

    public Float getTemperatureReturnOnMaxCurvePoint() {
        return temperatureReturnOnMaxCurvePoint;
    }

    public Float getTemperatureReturnOnMinCurvePoint() {
        return temperatureReturnOnMinCurvePoint;
    }

    public Float getTemperatureReturnMin() {
        return temperatureReturnMin;
    }

    public Float getTemperatureWeatherCurveMin() {
        return temperatureWeatherCurveMin;
    }

    public Float getTemperatureWeatherCurveMax() {
        return temperatureWeatherCurveMax;
    }

    public Float getWaterFlow() {
        return waterFlow;
    }
}

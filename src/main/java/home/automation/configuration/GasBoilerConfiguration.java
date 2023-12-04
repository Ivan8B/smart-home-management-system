package home.automation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GasBoilerConfiguration {
    @Value("${gasBoiler.relay.address}")
    private Integer address;

    @Value("${gasBoiler.relay.coil}")
    private Integer coil;

    @Value("${gasBoiler.relay.turnOffDirectDelta}")
    private Float turnOffDirectDelta;

    @Value("${gasBoiler.temperature.return.min}")
    private Float temperatureReturnMin;

    @Value("${gasBoiler.temperature.return.max}")
    private Float temperatureReturnMax;

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

    public Float getTurnOffDirectDelta() {
        return turnOffDirectDelta;
    }

    public Float getTemperatureReturnMin() {
        return temperatureReturnMin;
    }

    public Float getTemperatureReturnMax() {
        return temperatureReturnMax;
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

package home.automation.service.impl;

import home.automation.configuration.UniversalSensorsConfiguration;
import home.automation.enums.UniversalSensor;
import home.automation.event.error.UniversalSensorPollErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.model.UniversalSensorData;
import home.automation.service.ModbusService;
import home.automation.service.UniversalSensorsService;
import home.automation.utils.P_F;
import home.automation.utils.PPM_F;
import home.automation.utils.decimal.TD_F;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@CacheConfig(cacheNames = {"universal_sensors_cache"})
public class UniversalSensorsServiceImpl implements UniversalSensorsService {
    private static final Logger logger = LoggerFactory.getLogger(UniversalSensorsServiceImpl.class);
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UniversalSensorsConfiguration configuration;
    private final ModbusService modbusService;

    public UniversalSensorsServiceImpl(
            ApplicationEventPublisher applicationEventPublisher,
            UniversalSensorsConfiguration configuration,
            ModbusService modbusService,
            MeterRegistry meterRegistry
    ) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.configuration = configuration;
        this.modbusService = modbusService;

        for (UniversalSensor sensor : UniversalSensor.values()) {
            Gauge.builder("temperature", bind(this::getCurrentTemperatureForSensor, sensor))
                    .tag("system", "home_automation")
                    .tag("component", sensor.name())
                    .description(sensor.getTemplate())
                    .register(meterRegistry);
        }

        for (UniversalSensor sensor : UniversalSensor.values()) {
            Gauge.builder("humidity_percent", bind(this::getCurrentHumidityPercentForSensor, sensor))
                    .tag("system", "home_automation")
                    .tag("component", sensor.name())
                    .description(sensor.getTemplate())
                    .register(meterRegistry);
        }

        for (UniversalSensor sensor : UniversalSensor.values()) {
            Gauge.builder("CO2_ppm", bind(this::getCurrentCO2ppmForSensor, sensor))
                    .tag("system", "home_automation")
                    .tag("component", sensor.name())
                    .description(sensor.getTemplate())
                    .register(meterRegistry);
        }
    }

    private <T, R> Supplier<R> bind(Function<T, R> fn, T val) {
        return () -> fn.apply(val);
    }

    @Override
    public Float getCurrentTemperatureForSensor(UniversalSensor sensor) {
        UniversalSensorData data = getUniversalSensorData(sensor);
        if (data == null) {
            return null;
        }
        return data.getTemperature();
    }

    @Override
    public Integer getCurrentHumidityPercentForSensor(UniversalSensor sensor) {
        UniversalSensorData data = getUniversalSensorData(sensor);
        if (data == null) {
            return null;
        }
        return data.getHumidityPercent();
    }

    @Override
    public Integer getCurrentCO2ppmForSensor(UniversalSensor sensor) {
        UniversalSensorData data = getUniversalSensorData(sensor);
        if (data == null) {
            return null;
        }
        return data.getCO2ppm();
    }

    @Cacheable("universal_sensors_cache")
    private UniversalSensorData getUniversalSensorData(UniversalSensor sensor) {
        try {
            int[] values = modbusService.readHoldingRegisters(configuration.getUniversalSensorAddress(sensor), 0, 3);
            return new UniversalSensorData(((float)values[1])/10, Math.round((float)values[0]/10), values[2]);
        } catch (ModbusException e) {
            logger.error("{} - ошибка опроса, modbus адрес {}", sensor.getTemplate(),
                    configuration.getUniversalSensorAddress(sensor));
            logger.debug("Отправляем событие об ошибке поллинга универстального датчика в {}", sensor.getRoom());
            applicationEventPublisher.publishEvent(new UniversalSensorPollErrorEvent(this, sensor));
            return null;
        }
    }

    private String getCurrentParamsFromUniversalSensorFormatted(UniversalSensor sensor) {
        Float temperature = getCurrentTemperatureForSensor(sensor);
        String temperatureFormatted = (temperature != null) ? TD_F.format(temperature) : "ошибка опроса температуры";

        Integer humidityPercent = getCurrentHumidityPercentForSensor(sensor);
        String humidityPercentFormatted = (humidityPercent != null) ? P_F.format(humidityPercent) : "ошибка опроса влажности";

        Integer co2ppm = getCurrentCO2ppmForSensor(sensor);
        String co2ppmFormatted = (co2ppm != null) ? PPM_F.format(co2ppm) : "ошибка опроса CO2";

        return sensor.getTemplate() + " " + temperatureFormatted + ", " + humidityPercentFormatted + ", " + co2ppmFormatted;
    }

    @Override
    public String getCurrentParamsFormatted() {
        return Arrays.stream(UniversalSensor.values()).map(this::getCurrentParamsFromUniversalSensorFormatted)
                .collect(Collectors.joining("\n* "));
    }
}

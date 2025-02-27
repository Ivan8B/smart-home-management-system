package home.automation.service.impl;

import home.automation.configuration.TemperatureSensorsBoardsConfiguration;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.TemperatureSensorPollErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
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
@CacheConfig(cacheNames = {"temperature_sensors_cache"})
public class TemperatureSensorsServiceImpl implements TemperatureSensorsService {
    public static final Integer TEMPERATURE_SENSOR_BORDER_VALUE = Integer.parseInt("1000000000000000", 2);
    public static final Integer TEMPERATURE_SENSOR_SUBTRACTING = 65536;
    private static final Logger logger = LoggerFactory.getLogger(TemperatureSensorsServiceImpl.class);
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TemperatureSensorsBoardsConfiguration configuration;
    private final ModbusService modbusService;

    public TemperatureSensorsServiceImpl(
            ApplicationEventPublisher applicationEventPublisher,
            TemperatureSensorsBoardsConfiguration configuration,
            ModbusService modbusService,
            MeterRegistry meterRegistry
    ) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.configuration = configuration;
        this.modbusService = modbusService;

        for (TemperatureSensor sensor : TemperatureSensor.values()) {
            Gauge.builder("temperature", bind(this::getCurrentTemperatureForSensor, sensor))
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
    @Cacheable("temperature_sensors_cache")
    public Float getCurrentTemperatureForSensor(TemperatureSensor sensor) {
        try {
            int rawTemperature =
                    modbusService.readHoldingRegister(configuration.getAddressByName(sensor.getBoardName()),
                            sensor.getRegisterId()
                    );
            if (rawTemperature == TEMPERATURE_SENSOR_BORDER_VALUE) {
                logger.error("Ошибка опроса  - температурный сенсор DS18B20 не подключен, регистр {}",
                        sensor.getRegisterId());
                throw new ModbusException(
                        "Ошибка опроса  - температурный сенсор DS18B20 не подключен, регистр " +
                                sensor.getRegisterId());
            }
            /* если старший бит единица - температура отрицательная и из нее нужно вычитать, смотри документацию
            R4DCB08*/
            if (rawTemperature > TEMPERATURE_SENSOR_BORDER_VALUE) {
                rawTemperature = rawTemperature - TEMPERATURE_SENSOR_SUBTRACTING;
            }
            return (float) rawTemperature / 10;
        } catch (ModbusException e) {
            logger.error("{} - ошибка опроса, адрес регистра {}", sensor.getTemplate(), sensor.getRegisterId());
            logger.debug("Отправляем событие об ошибке поллинга сенсора по адресу регистра {}", sensor.getRegisterId());
            applicationEventPublisher.publishEvent(new TemperatureSensorPollErrorEvent(this, sensor));
            return null;
        }
    }

    private String getCurrentTemperatureForSensorFormatted(TemperatureSensor sensor) {
        Float temperature = getCurrentTemperatureForSensor(sensor);
        if (temperature == null) {
            return sensor.getTemplate() + " - ошибка опроса!";
        }
        return sensor.getTemplate() + " " + TD_F.format(temperature);
    }

    @Override
    public String getCurrentTemperaturesFormatted() {
        return Arrays.stream(TemperatureSensor.values()).map(this::getCurrentTemperatureForSensorFormatted)
                .collect(Collectors.joining("\n* "));
    }
}

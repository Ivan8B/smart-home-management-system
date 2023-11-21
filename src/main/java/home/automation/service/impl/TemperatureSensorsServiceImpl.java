package home.automation.service.impl;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import home.automation.configuration.TemperatureSensorsBoardsConfiguration;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.TemperatureSensorPollErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class TemperatureSensorsServiceImpl implements TemperatureSensorsService {
    public static final Integer TEMPERATURE_SENSOR_ERROR_VALUE = 32768;
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
    public @Nullable Float getCurrentTemperatureForSensor(TemperatureSensor sensor) {
        try {
            int rawTemperature =
                modbusService.readHoldingRegister(configuration.getAddressByName(sensor.getBoardName()),
                    sensor.getRegisterId()
                );
            if (rawTemperature == TEMPERATURE_SENSOR_ERROR_VALUE) {
                throw new ModbusException(
                    "Ошибка опроса  - температурный сенсор DS18B20 не подключен, регистр " + sensor.getRegisterId());
            }
            /* такая логика работы R4DCB08, смотри документацию */
            if (rawTemperature >= Integer.parseInt("FF00", 16)) {
                rawTemperature = rawTemperature - Integer.parseInt("10000", 16);
            }
            return (float) rawTemperature / 10;
        } catch (ModbusException e) {
            logger.error("{} - ошибка опроса, адрес {}", sensor.getTemplate(), sensor.getRegisterId());
            logger.debug("Отправляем событие об ошибке поллинга сенсора {}", sensor.getRegisterId());
            applicationEventPublisher.publishEvent(new TemperatureSensorPollErrorEvent(this, sensor));
            return null;
        }
    }

    private String getCurrentTemperatureForSensorFormatted(TemperatureSensor sensor) {
        Float temperature = getCurrentTemperatureForSensor(sensor);
        if (temperature == null) {
            return sensor.getTemplate() + " - ошибка опроса!";
        }
        DecimalFormat df = new DecimalFormat("#.#");
        return sensor.getTemplate() + " " + df.format(temperature) + " C°";
    }

    @Override
    public String getCurrentTemperaturesFormatted() {
        return Arrays.stream(TemperatureSensor.values()).map(this::getCurrentTemperatureForSensorFormatted)
            .collect(Collectors.joining("\n* "));
    }
}

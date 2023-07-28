package home.automation.service.impl;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.stream.Collectors;

import home.automation.configuration.TemperatureSensorsBoardConfiguration;
import home.automation.enums.TemperatureSensor;
import home.automation.event.TemperatureSensorPollErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class TemperatureSensorsServiceImpl implements TemperatureSensorsService {
    private static final Logger logger = LoggerFactory.getLogger(TemperatureSensorsServiceImpl.class);

    public static final Integer TEMPERATURE_SENSOR_ERROR_VALUE = 32768;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final TemperatureSensorsBoardConfiguration configuration;

    private final ModbusService modbusService;

    public TemperatureSensorsServiceImpl(
        ApplicationEventPublisher applicationEventPublisher,
        TemperatureSensorsBoardConfiguration configuration,
        ModbusService modbusService
    ) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.configuration = configuration;
        this.modbusService = modbusService;
    }

    @Override
    public @Nullable Float getCurrentTemperatureForSensor(TemperatureSensor sensor) {
        try {
            int rawTemperature = modbusService.readHoldingRegister(sensor.getRegisterId(), configuration.getAddress());
            if (rawTemperature == TEMPERATURE_SENSOR_ERROR_VALUE) {
                throw new ModbusException(
                    "Ошибка опроса  - температурный сенсор DS18B20 не подключен, регистр " + sensor.getRegisterId());
            }
            return (float) (rawTemperature / 10);
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
            return sensor.getTemplate() + "- ошибка опроса!";
        }
        DecimalFormat df = new DecimalFormat("#.#");
        return sensor.getTemplate() + "  " + df.format(temperature) + " C°";
    }

    @Override
    public String getCurrentTemperaturesFormatted() {
        return Arrays.stream(TemperatureSensor.values()).map(this::getCurrentTemperatureForSensorFormatted)
            .collect(Collectors.joining("\n"));
    }
}
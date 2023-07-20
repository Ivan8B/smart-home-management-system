package home.heating.service.impl;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.stream.Collectors;

import home.heating.configuration.TemperatureSensorsBoardConfiguration;
import home.heating.enums.TemperatureSensor;
import home.heating.event.MinimalTemperatureLowEvent;
import home.heating.event.TemperatureSensorPollErrorEvent;
import home.heating.exception.ModbusException;
import home.heating.service.ModbusService;
import home.heating.service.TemperatureSensorsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
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

    @Scheduled(fixedRateString = "${health.minimalTemperature.pollInterval}")
    private void checkMinimalTemperature() {
        Arrays.stream(TemperatureSensor.values())
            .filter(sensor -> sensor.isCritical() && sensor.getMinimalTemperature() != null).forEach(sensor -> {
                Float currentTemperatureForSensor = getCurrentTemperatureForSensor(sensor);
                if (currentTemperatureForSensor != null && currentTemperatureForSensor < sensor.getMinimalTemperature()) {
                    logger.warn(sensor.getTemplate() + " - слишком низкая температура!");
                    logger.debug("Отправляем событие о низкой температуре");
                    applicationEventPublisher.publishEvent(new MinimalTemperatureLowEvent(this, sensor));
                }
            });
    }

    @Override
    public Float getCurrentTemperatureForSensor(TemperatureSensor sensor) {
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
            publishEvent(sensor);
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

    private void publishEvent(TemperatureSensor sensor) {
        TemperatureSensorPollErrorEvent event = new TemperatureSensorPollErrorEvent(this, sensor);
        applicationEventPublisher.publishEvent(event);
    }
}

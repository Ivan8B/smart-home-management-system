package home.automation.service.impl;

import home.automation.configuration.ElectricBoilerConfiguration;
import home.automation.enums.ElectricBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.ElectricBoilerErrorEvent;
import home.automation.event.info.ElectricBoilerTurnedOnEvent;
import home.automation.exception.ModbusException;
import home.automation.service.ElectricBoilerService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ElectricBoilerServiceImpl implements ElectricBoilerService {
    private final Logger logger = LoggerFactory.getLogger(ElectricBoilerServiceImpl.class);

    private final ElectricBoilerConfiguration configuration;

    private final TemperatureSensorsService temperatureSensorsService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ModbusService modbusService;

    public ElectricBoilerServiceImpl(
        ElectricBoilerConfiguration configuration,
        TemperatureSensorsService temperatureSensorsService,
        ApplicationEventPublisher applicationEventPublisher,
        ModbusService modbusService
    ) {
        this.configuration = configuration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.modbusService = modbusService;
    }

    @Scheduled(fixedRateString = "${electricBoiler.controlInterval}")
    private void control() {
        logger.debug("Запущена задача управления электрическим котлом");
        logger.debug("Опрашиваем сенсор температуры в котельной");
        Float currentTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE);

        if (currentTemperature == null) {
            logger.error("Ошибка получения температуры в котельной");
            logger.debug("Отправляем событие по отказу работы с электрическим котлом");
            applicationEventPublisher.publishEvent(new ElectricBoilerErrorEvent(this));
            return;
        }

        if (currentTemperature < TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimalTemperature()) {
            logger.debug("Требуется включение электрического котла, включаем");
            turnOn();
            applicationEventPublisher.publishEvent(new ElectricBoilerTurnedOnEvent(this));
            return;
        }
        if (currentTemperature
            > TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimalTemperature() + configuration.getHysteresis()) {
            logger.debug("Работа электрического котла не требуется, выключаем");
            turnOff();
        }
    }

    private void turnOn() {
        if (getStatus() != ElectricBoilerStatus.TURNED_ON) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле");
                applicationEventPublisher.publishEvent(new ElectricBoilerErrorEvent(this));
            }
        }
    }

    private void turnOff() {
        if (getStatus() != ElectricBoilerStatus.TURNED_OFF) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле");
                applicationEventPublisher.publishEvent(new ElectricBoilerErrorEvent(this));
            }
        }
    }

    @Override
    public ElectricBoilerStatus getStatus() {
        try {
            boolean[] pollResult = modbusService.readAllCoilsFromZero(configuration.getAddress());
            if (pollResult.length < 1) {
                throw new ModbusException("Опрос катушек вернул пустой массив");
            }
            if (pollResult[configuration.getCoil()]) {
                return ElectricBoilerStatus.TURNED_ON;
            } else {
                return ElectricBoilerStatus.TURNED_OFF;
            }

        } catch (ModbusException e) {
            logger.error("Ошибка получения статуса реле электрического котла", e);
            applicationEventPublisher.publishEvent(new ElectricBoilerErrorEvent(this));
            return ElectricBoilerStatus.ERROR;
        }
    }

    @Override
    public String getFormattedStatus() {
        return getStatus().getTemplate();
    }
}

package home.automation.service.impl;

import home.automation.configuration.ElectricBoilerConfiguration;
import home.automation.enums.ElectricBoilerStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.HeatRequestStatus;
import home.automation.enums.HeatingPumpsStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.ElectricBoilerErrorEvent;
import home.automation.event.info.ElectricBoilerTurnedOnEvent;
import home.automation.exception.ModbusException;
import home.automation.service.ElectricBoilerService;
import home.automation.service.GasBoilerService;
import home.automation.service.HeatRequestService;
import home.automation.service.HeatingPumpsService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import home.automation.utils.decimal.TD_F;
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

    private final HeatRequestService heatRequestService;

    private final HeatingPumpsService heatingPumpsService;

    private final GasBoilerService gasBoilerService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ModbusService modbusService;

    public ElectricBoilerServiceImpl(
            ElectricBoilerConfiguration configuration,
            TemperatureSensorsService temperatureSensorsService,
            HeatRequestService heatRequestService,
            HeatingPumpsService heatingPumpsService,
            GasBoilerService gasBoilerService,
            ApplicationEventPublisher applicationEventPublisher,
            ModbusService modbusService
    ) {
        this.configuration = configuration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.heatRequestService = heatRequestService;
        this.heatingPumpsService = heatingPumpsService;
        this.gasBoilerService = gasBoilerService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.modbusService = modbusService;
    }

    @Scheduled(fixedRateString = "${electricBoiler.controlInterval}")
    private void control() {
        logger.debug("Запущена задача управления электрическим котлом");

        Float currentTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE);
        logger.debug("Температура в котельной {}", TD_F.format(currentTemperature));

        if (currentTemperature == null) {
            logger.error("Ошибка получения температуры в котельной");
            logger.debug("Отправляем событие по отказу работы с электрическим котлом");
            applicationEventPublisher.publishEvent(new ElectricBoilerErrorEvent(this));
            return;
        }

        if (currentTemperature < TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimumTemperature() &&
                heatRequestService.getStatus() != HeatRequestStatus.NO_NEED_HEAT &&
                heatingPumpsService.getStatus() != HeatingPumpsStatus.TURNED_OFF &&
                gasBoilerService.getStatus() != GasBoilerStatus.WORKS
                ) {
            logger.debug("Требуется включение электрического котла, включаем");
            turnOn();
            applicationEventPublisher.publishEvent(new ElectricBoilerTurnedOnEvent(this));
            return;
        }
        if (currentTemperature > TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimumTemperature() + configuration.getHysteresis() ||
                heatRequestService.getStatus() == HeatRequestStatus.NO_NEED_HEAT ||
                heatingPumpsService.getStatus() == HeatingPumpsStatus.TURNED_OFF ||
                gasBoilerService.getStatus() == GasBoilerStatus.WORKS
                ) {
            logger.debug("Работа электрического котла не требуется, выключаем");
            turnOff();
        }
    }

    private void turnOn() {
        if (getStatus() != ElectricBoilerStatus.TURNED_ON) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле электрического котла");
                applicationEventPublisher.publishEvent(new ElectricBoilerErrorEvent(this));
            }
        } else {
            logger.debug("Реле электрического котла уже включено");
        }
    }

    private void turnOff() {
        if (getStatus() != ElectricBoilerStatus.TURNED_OFF) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле электрического котла");
                applicationEventPublisher.publishEvent(new ElectricBoilerErrorEvent(this));
            }
        } else {
            logger.debug("Реле электрического котла уже отключено");
        }
    }

    @Override
    public ElectricBoilerStatus getStatus() {
        try {
            boolean[] pollResult = modbusService.readAllCoilsFromZero(configuration.getAddress());
            if (pollResult.length < 1) {
                throw new ModbusException("Опрос катушек реле электрического котла вернул пустой массив");
            }
            if (pollResult[configuration.getCoil()]) {
                return ElectricBoilerStatus.TURNED_ON;
            }
            else {
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

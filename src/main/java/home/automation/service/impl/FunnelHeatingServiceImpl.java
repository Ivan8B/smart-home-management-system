package home.automation.service.impl;

import home.automation.configuration.FunnelHeatingConfiguration;
import home.automation.enums.FunnelHeatingStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.FunnelHeatingErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.FunnelHeatingService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FunnelHeatingServiceImpl implements FunnelHeatingService {
    private final Logger logger = LoggerFactory.getLogger(FunnelHeatingServiceImpl.class);

    private final FunnelHeatingConfiguration configuration;

    private final TemperatureSensorsService temperatureSensorsService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ModbusService modbusService;

    public FunnelHeatingServiceImpl(
        FunnelHeatingConfiguration configuration,
        TemperatureSensorsService temperatureSensorsService,
        ApplicationEventPublisher applicationEventPublisher,
        ModbusService modbusService
    ) {
        this.configuration = configuration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.modbusService = modbusService;
    }

    @Scheduled(fixedRateString = "${funnelHeating.controlInterval}")
    private void control() {
        logger.debug("Запущена задача управления воронками обогрева");
        logger.debug("Опрашиваем сенсор уличной температуры");
        Float currentTemperature = temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);

        if (currentTemperature == null) {
            logger.error("Ошибка получения температуры на улице");
            logger.debug("Отправляем событие по отказу работы с обогревом воронок");
            applicationEventPublisher.publishEvent(new FunnelHeatingErrorEvent(this));
            return;
        }

        if (configuration.getTemperatureMin() < currentTemperature
            && currentTemperature < configuration.getTemperatureMax()) {
            logger.debug("Требуется подогрев воронок, включаем");
            turnOn();
        } else {
            logger.debug("Подогрева воронок не требуется, отключаем");
            turnOff();
        }
    }

    private void turnOn() {
        if (getStatus() != FunnelHeatingStatus.TURNED_ON) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле обогрева воронок");
                applicationEventPublisher.publishEvent(new FunnelHeatingErrorEvent(this));
            }
        }
    }

    private void turnOff() {
        if (getStatus() != FunnelHeatingStatus.TURNED_OFF) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле обогрева воронок");
                applicationEventPublisher.publishEvent(new FunnelHeatingErrorEvent(this));
            }
        }
    }

    @Override
    public FunnelHeatingStatus getStatus() {
        try {
            boolean[] pollResult = modbusService.readAllCoilsFromZero(configuration.getAddress());
            if (pollResult.length < 1) {
                throw new ModbusException("Опрос катушек реле обогрева воронок вернул пустой массив");
            }
            if (pollResult[configuration.getCoil()]) {
                return FunnelHeatingStatus.TURNED_ON;
            } else {
                return FunnelHeatingStatus.TURNED_OFF;
            }

        } catch (ModbusException e) {
            logger.error("Ошибка получения статуса реле обогрева воронок", e);
            applicationEventPublisher.publishEvent(new FunnelHeatingErrorEvent(this));
            return FunnelHeatingStatus.ERROR;
        }
    }

    @Override
    public String getFormattedStatus() {
        return getStatus().getTemplate();
    }
}

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

    private final TemperatureSensor outsideTemperature = TemperatureSensor.OUTSIDE_TEMPERATURE;

    private final FunnelHeatingConfiguration configuration;

    private final TemperatureSensorsService temperatureSensorsService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ModbusService modbusService;

    private FunnelHeatingStatus status = FunnelHeatingStatus.INIT;

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
        Float currentTemperature = temperatureSensorsService.getCurrentTemperatureForSensor(outsideTemperature);

        if (currentTemperature == null) {
            logger.error("Ошибка получения температуры на улице");
            logger.debug("Отправляем событие по отказу работы с обогревом воронок");
            applicationEventPublisher.publishEvent(new FunnelHeatingErrorEvent(this));
            status = FunnelHeatingStatus.ERROR;
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
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
            status = FunnelHeatingStatus.TURNED_ON;
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            status = FunnelHeatingStatus.ERROR;
            applicationEventPublisher.publishEvent(new FunnelHeatingErrorEvent(this));
        }
    }

    private void turnOff() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
            status = FunnelHeatingStatus.TURNED_OFF;
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            status = FunnelHeatingStatus.ERROR;
            applicationEventPublisher.publishEvent(new FunnelHeatingErrorEvent(this));
        }
    }

    @Override
    public String getFormattedStatus() {
        return status.getTemplate();
    }
}

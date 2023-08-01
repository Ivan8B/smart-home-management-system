package home.automation.service.impl;

import ca.rmen.sunrisesunset.SunriseSunset;
import home.automation.configuration.StreetLightConfiguration;
import home.automation.event.StreetLightRelaySetFailEvent;
import home.automation.exception.ModbusException;
import home.automation.service.ModbusService;
import home.automation.service.StreetLightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StreetLightServiceImpl implements StreetLightService {

    private static final Logger logger = LoggerFactory.getLogger(StreetLightServiceImpl.class);

    private final StreetLightConfiguration configuration;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    public StreetLightServiceImpl(
        StreetLightConfiguration configuration, ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedRateString = "${streetLight.calculateInterval}")
    private void control() {
        logger.debug("Запущена задача расчета освещенности на улице");
        if (SunriseSunset.isNight(configuration.getLatitude(), configuration.getLongitude())) {
            logger.debug("Нужно освещение, включаем");
            turnOn();
        } else {
            logger.debug("Освещение не требуется, выключаем");
            turnOff();
        }
    }

    private void turnOn() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new StreetLightRelaySetFailEvent(this));
        }
    }

    private void turnOff() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new StreetLightRelaySetFailEvent(this));
        }
    }
}

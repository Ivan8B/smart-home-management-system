package home.automation.service.impl;

import java.util.Calendar;

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

import static ca.rmen.sunrisesunset.SunriseSunset.getCivilTwilight;

@Service
public class StreetLightServiceImpl implements StreetLightService {

    private static final Logger logger = LoggerFactory.getLogger(StreetLightServiceImpl.class);

    private final StreetLightConfiguration configuration;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    public StreetLightServiceImpl(
        StreetLightConfiguration configuration,
        ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedRateString = "${streetLight.controlInterval}")
    private void control() {
        logger.debug("Запущена задача расчета освещенности на улице");
        Calendar today = Calendar.getInstance();
        /* вынесено в отдельный метод для удобства тестирования */
        control(today);
    }

    private void control(Calendar calendar) {
        /* гражданские сумерки на широте Москвы есть всегда, поэтому не боимся получить ошибку */
        Calendar[] civilTwilight = getCivilTwilight(calendar, configuration.getLatitude(), configuration.getLongitude());

        if (calendar.after(civilTwilight[0]) && calendar.before(civilTwilight[1])) {
            logger.debug("Освещение не требуется, выключаем");
            turnOff();
        } else {
            logger.debug("Нужно освещение, включаем");
            turnOn();
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

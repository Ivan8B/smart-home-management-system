package home.automation.service.impl;

import java.util.Calendar;

import home.automation.configuration.StreetLightConfiguration;
import home.automation.enums.StreetLightStatus;
import home.automation.event.error.StreetLightErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.ModbusService;
import home.automation.service.StreetLightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static ca.rmen.sunrisesunset.SunriseSunset.getSunriseSunset;

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
        /* восход и закат на широте Москвы есть всегда, поэтому не боимся получить ошибку */
        Calendar[] sunriseSunset =
            getSunriseSunset(calendar, configuration.getLatitude(), configuration.getLongitude());

        if (calendar.after(sunriseSunset[0]) && calendar.before(sunriseSunset[1])) {
            logger.debug("Освещение не требуется, выключаем");
            turnOff();
        } else {
            logger.debug("Нужно освещение, включаем");
            turnOn();
        }
    }

    private void turnOn() {
        if (getStatus() != StreetLightStatus.TURNED_ON) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле уличного освещения");
                applicationEventPublisher.publishEvent(new StreetLightErrorEvent(this));
            }
        }
    }

    private void turnOff() {
        if (getStatus() != StreetLightStatus.TURNED_OFF) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле уличного освещения");
                applicationEventPublisher.publishEvent(new StreetLightErrorEvent(this));
            }
        }
    }

    @Override
    public StreetLightStatus getStatus() {
        try {
            boolean[] pollResult = modbusService.readAllCoilsFromZero(configuration.getAddress());
            if (pollResult.length < 1) {
                throw new ModbusException("Опрос катушек реле уличного освещения вернул пустой массив");
            }
            if (pollResult[configuration.getCoil()]) {
                return StreetLightStatus.TURNED_ON;
            } else {
                return StreetLightStatus.TURNED_OFF;
            }

        } catch (ModbusException e) {
            logger.error("Ошибка получения статуса реле уличного освещения", e);
            applicationEventPublisher.publishEvent(new StreetLightErrorEvent(this));
            return StreetLightStatus.ERROR;
        }
    }

    @Override
    public String getFormattedStatus() {
        return getStatus().getTemplate();
    }
}

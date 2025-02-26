package home.automation.service.impl;

import home.automation.configuration.GeneralConfiguration;
import home.automation.enums.HeatRequestStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.HeatRequestErrorEvent;
import home.automation.service.HeatRequestService;
import home.automation.service.TemperatureSensorsService;
import home.automation.utils.decimal.TD_F;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HeatRequestServiceImpl implements HeatRequestService {
    private static final Logger logger = LoggerFactory.getLogger(HeatRequestServiceImpl.class);
    private final GeneralConfiguration configuration;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TemperatureSensorsService temperatureSensorsService;
    private HeatRequestStatus calculatedStatus = HeatRequestStatus.NEED_HEAT;

    public HeatRequestServiceImpl(
            GeneralConfiguration configuration,
            ApplicationEventPublisher applicationEventPublisher,
            TemperatureSensorsService temperatureSensorsService
    ) {
        this.configuration = configuration;
        this.applicationEventPublisher = applicationEventPublisher;
        this.temperatureSensorsService = temperatureSensorsService;
    }

    @Override
    public HeatRequestStatus getStatus() {
        return calculatedStatus;
    }

    @Override
    public String getFormattedStatus() {
        return calculatedStatus.getTemplate();
    }

    @Scheduled(fixedRateString = "${temperature.controlInterval}")
    private void control() {
        logger.debug("Запущена задача расчета статуса запроса на тепло в дом");

        Float currentTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);
        logger.debug("Температура на улице {}", TD_F.format(currentTemperature));

        if (currentTemperature == null) {
            logger.error("Ошибка получения температуры на улице");
            applicationEventPublisher.publishEvent(new HeatRequestErrorEvent(this));
            calculatedStatus = HeatRequestStatus.ERROR;
            return;
        }

        if (currentTemperature < configuration.getOutsideMax() - configuration.getHysteresis()) {
            logger.debug("Есть запрос на тепло в дом");
            calculatedStatus = HeatRequestStatus.NEED_HEAT;
        }

        if (currentTemperature > configuration.getOutsideMax()) {
            logger.debug("Нет запроса на тепло в дом");
            calculatedStatus = HeatRequestStatus.NO_NEED_HEAT;
        }
    }
}

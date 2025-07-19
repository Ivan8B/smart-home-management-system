package home.automation.service.impl;

import home.automation.configuration.GeneralConfiguration;
import home.automation.enums.HeatRequestStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.HeatRequestErrorEvent;
import home.automation.service.HeatRequestService;
import home.automation.service.TemperatureSensorsService;
import home.automation.utils.decimal.TD_F;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class HeatRequestServiceImpl implements HeatRequestService {
    private static final Logger logger = LoggerFactory.getLogger(HeatRequestServiceImpl.class);
    private final Set<TemperatureSensor> averageInternalSensors = Set.of(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE);
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

        Float currentOutsideTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);
        logger.debug("Температура на улице {}", TD_F.format(currentOutsideTemperature));

        if (currentOutsideTemperature == null) {
            logger.warn("Ошибка получения температуры на улице");
            applicationEventPublisher.publishEvent(new HeatRequestErrorEvent(this));
            calculatedStatus = HeatRequestStatus.ERROR;
            return;
        }

        if (currentOutsideTemperature > configuration.getOutsideMax()) {
            logger.info("Нет запроса на тепло в дом");
            calculatedStatus = HeatRequestStatus.NO_NEED_HEAT;
            return;
        }

        if (currentOutsideTemperature < configuration.getOutsideMax() - configuration.getOutsideHysteresis()) {
            logger.info("Есть запрос на тепло в дом");
            calculatedStatus = HeatRequestStatus.NEED_HEAT;
            return;
        }

        if (currentOutsideTemperature > configuration.getOutsideMax() - configuration.getOutsideHysteresis()) {
            logger.debug("Температура на улице промежуточная, проверяем температуру в доме");

            Float currentAverageInsideTemperature = calculateAverageInternalTemperature();
            if (currentAverageInsideTemperature == null) {
                logger.warn("Ошибка получения средней температуры в доме");
                applicationEventPublisher.publishEvent(new HeatRequestErrorEvent(this));
                calculatedStatus = HeatRequestStatus.ERROR;
                return;
            }

            if (currentAverageInsideTemperature > configuration.getInsideTarget()) {
                logger.info("Нет запроса на тепло в дом");
                calculatedStatus = HeatRequestStatus.NO_NEED_HEAT;
                return;
            }

            if (currentAverageInsideTemperature < configuration.getInsideTarget() - configuration.getInsideHysteresis()) {
                logger.info("Есть запрос на тепло в дом");
                calculatedStatus = HeatRequestStatus.NEED_HEAT;
                return;
            }

            if (currentAverageInsideTemperature > configuration.getInsideTarget() - configuration.getInsideHysteresis()) {
                logger.debug("Оставляем запрос на тепло в том же положении, но если была ошибка - даем запрос");
                if (calculatedStatus == HeatRequestStatus.ERROR) {
                    calculatedStatus = HeatRequestStatus.NEED_HEAT;
                }
                return;
            }
        }
    }

    private @Nullable Float calculateAverageInternalTemperature() {
        Set<Float> polledTemperatures = new HashSet<>();
        for (TemperatureSensor sensor : averageInternalSensors) {
            Float sensorTemperature = temperatureSensorsService.getCurrentTemperatureForSensor(sensor);
            if (sensorTemperature == null) {
                logger.info(sensor.getTemplate() + " исключена из расчета средней");
                continue;
            }
            polledTemperatures.add(sensorTemperature);
        }
        if (polledTemperatures.isEmpty()) {
            logger.warn("Не удалось рассчитать среднюю температуру");
            return null;
        }
        else {
            float sum = 0;
            for (Float temperature : polledTemperatures) {
                sum = sum + temperature;
            }
            return sum / polledTemperatures.size();
        }
    }
}

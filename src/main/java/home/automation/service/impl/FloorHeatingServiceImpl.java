package home.automation.service.impl;

import java.util.HashSet;
import java.util.Set;

import home.automation.configuration.FloorHeatingConfiguration;
import home.automation.enums.FloorHeatingStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.FloorHeatingStatusCalculateErrorEvent;
import home.automation.event.info.FloorHeatingStatusCalculatedEvent;
import home.automation.service.FloorHeatingService;
import home.automation.service.TemperatureSensorsService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FloorHeatingServiceImpl implements FloorHeatingService {

    private static final Logger logger = LoggerFactory.getLogger(FloorHeatingServiceImpl.class);

    private FloorHeatingStatus calculatedStatus = FloorHeatingStatus.INIT;

    private final Set<TemperatureSensor> averageInternalSensors =
        Set.of(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE, TemperatureSensor.SECOND_FLOOR_BATHROOM_TEMPERATURE);

    private final FloorHeatingConfiguration configuration;

    private final TemperatureSensorsService temperatureSensorsService;

    private final ApplicationEventPublisher applicationEventPublisher;

    public FloorHeatingServiceImpl(
        FloorHeatingConfiguration configuration,
        TemperatureSensorsService temperatureSensorsService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.configuration = configuration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedRateString = "${floorHeating.controlInterval}")
    private void control() {
        logger.debug("Запущена джоба управления теплым полом");

        logger.debug("Опрашиваем датчики для расчета целевой температуры подачи");
        Float averageInternalTemperature = calculateAverageInternalTemperature();
        Float outsideTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);

        if (averageInternalTemperature != null) {
            logger.debug("Есть показания от датчиков в помещениях, приступаем к засчету запроса тепла");

            if (averageInternalTemperature < configuration.getDirectMinTemperature()) {
                logger.debug("Средняя температура в помещениях меньше целевой, отправляем запрос на тепло в теплые полы");
                calculatedStatus = FloorHeatingStatus.NEED_HEAT;
                publishCalculatedEvent(calculatedStatus);
            } else {
                logger.debug("Средняя температура в помещениях больше целевой, отправляем отказ от тепла в теплые полы");
                calculatedStatus = FloorHeatingStatus.NO_NEED_HEAT;
                publishCalculatedEvent(calculatedStatus);
            }

            if (outsideTemperature != null) {
                logger.debug(
                    "Есть показания от датчика на улице, приступаем к расчету целевой температуры подачи в теплые полы");
                Float targetDirectTemperature =
                    calculateTargetDirectTemperature(averageInternalTemperature, outsideTemperature);
                //TODO - добавить выставление целевой температуры на теплом полу через контроллер (с учетом статуса котла)
            } else {
                logger.warn("Невозможно расчитать целевую температуру подачи в теплые полы");
            }
        } else {
            logger.warn("Нет возможности определить температуру в помещениях, статус ERROR");
            calculatedStatus = FloorHeatingStatus.ERROR;
            publishCalculateErrorEvent();
        }
    }

    private float calculateTargetDirectTemperature(float averageInternalTemperature, float outsideTemperature) {
        logger.debug("Запущена задача расчета целевой температуры подачи в полы");
        /* Формула расчета : (Tцелевая -Tнаруж)*K + Tцелевая + (Тцелевая-Твпомещении) */
        float calculated = (configuration.getTargetTemperature() - outsideTemperature) * configuration.getK()
            + configuration.getTargetTemperature() + (configuration.getTargetTemperature()
            - averageInternalTemperature);

        if (calculated < configuration.getDirectMinTemperature()) {
            logger.debug(
                "Целевая температру подачи в полы меньше минимальной, возвращаем минимальную - {}",
                configuration.getDirectMinTemperature()
            );
            return configuration.getDirectMinTemperature();
        } else if (calculated > configuration.getDirectMaxTemperature()) {
            logger.debug(
                "Целевая температру подачи в полы больше максимальной, возвращаем максимальную - {}",
                configuration.getDirectMaxTemperature()
            );
            return configuration.getDirectMaxTemperature();
        } else {
            logger.debug("Целевая температура подачи в полы - {}", calculated);
            return calculated;
        }
    }

    private @Nullable Float calculateAverageInternalTemperature() {
        logger.debug("Запущен расчет средней температуры в доме для теплых полов");
        Set<Float> polledTemperatures = new HashSet<>();
        for (TemperatureSensor sensor : averageInternalSensors) {
            Float sensorTemperature = temperatureSensorsService.getCurrentTemperatureForSensor(sensor);
            if (sensorTemperature == null) {
                logger.info(sensor.getTemplate() + " исключена из расчета средней");
                continue;
            }
            polledTemperatures.add(sensorTemperature);
        }
        if (polledTemperatures.size() == 0) {
            logger.warn("Не удалось расчитать среднюю температуру");
            return null;
        } else {
            float sum = 0;
            for (Float temperature : polledTemperatures) {
                sum = sum + temperature;
            }
            return sum / polledTemperatures.size();
        }
    }

    private void publishCalculatedEvent(FloorHeatingStatus status) {
        FloorHeatingStatusCalculatedEvent event = new FloorHeatingStatusCalculatedEvent(this, status);
        applicationEventPublisher.publishEvent(event);
    }

    private void publishCalculateErrorEvent() {
        applicationEventPublisher.publishEvent(new FloorHeatingStatusCalculateErrorEvent(this));
    }

    @Override
    public FloorHeatingStatus getFloorHeatingStatus() {
        return calculatedStatus;
    }
}

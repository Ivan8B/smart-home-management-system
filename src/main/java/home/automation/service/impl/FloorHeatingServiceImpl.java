package home.automation.service.impl;

import java.util.HashSet;
import java.util.Set;

import home.automation.configuration.FloorHeatingTemperatureConfiguration;
import home.automation.configuration.FloorHeatingValveDacConfiguration;
import home.automation.configuration.FloorHeatingValveRelayConfiguration;
import home.automation.configuration.GeneralConfiguration;
import home.automation.enums.FloorHeatingStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.FloorHeatingErrorEvent;
import home.automation.event.info.FloorHeatingStatusCalculatedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.FloorHeatingService;
import home.automation.service.GasBoilerService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class FloorHeatingServiceImpl implements FloorHeatingService {

    private static final Logger logger = LoggerFactory.getLogger(FloorHeatingServiceImpl.class);

    private final Set<TemperatureSensor> averageInternalSensors =
        Set.of(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE, TemperatureSensor.SECOND_FLOOR_BATHROOM_TEMPERATURE);

    private final FloorHeatingTemperatureConfiguration temperatureConfiguration;

    private final FloorHeatingValveRelayConfiguration relayConfiguration;

    private final FloorHeatingValveDacConfiguration dacConfiguration;

    private final GeneralConfiguration generalConfiguration;

    private final TemperatureSensorsService temperatureSensorsService;

    private final GasBoilerService gasBoilerService;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private FloorHeatingStatus calculatedStatus = FloorHeatingStatus.INIT;

    public FloorHeatingServiceImpl(
        FloorHeatingTemperatureConfiguration temperatureConfiguration,
        FloorHeatingValveRelayConfiguration relayConfiguration,
        FloorHeatingValveDacConfiguration dacConfiguration,
        GeneralConfiguration generalConfiguration,
        TemperatureSensorsService temperatureSensorsService,
        @Lazy GasBoilerService gasBoilerService,
        ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.temperatureConfiguration = temperatureConfiguration;
        this.relayConfiguration = relayConfiguration;
        this.dacConfiguration = dacConfiguration;
        this.generalConfiguration = generalConfiguration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.gasBoilerService = gasBoilerService;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedRateString = "${floorHeating.controlInterval}")
    private void control() {
        logger.debug("Запущена джоба управления теплым полом");

        logger.debug("Опрашиваем датчики для расчета целевой температуры подачи");
        Float averageInternalTemperature = calculateAverageInternalTemperature();
        Float outsideTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);

        if (averageInternalTemperature == null) {
            logger.warn("Нет возможности определить среднюю температуру в помещениях, статус теплых полов ERROR");
            calculatedStatus = FloorHeatingStatus.ERROR;
            publishFloorHeatingErrorEvent();
            return;
        }

        if (outsideTemperature == null) {
            logger.warn("Нет возможности определить уличную температуру статус теплых полов ERROR");
            calculatedStatus = FloorHeatingStatus.ERROR;
            publishFloorHeatingErrorEvent();
            return;
        }

        if (averageInternalTemperature > generalConfiguration.getTargetTemperature()) {
            logger.debug("Средняя температура в помещениях больше целевой, отправляем отказ от тепла в теплые полы");
            calculatedStatus = FloorHeatingStatus.NO_NEED_HEAT;
            publishCalculatedEvent(calculatedStatus);
            return;
        }

        logger.debug("Средняя температура в помещениях меньше целевой, отправляем запрос на тепло в теплые полы");
        calculatedStatus = FloorHeatingStatus.NEED_HEAT;
        publishCalculatedEvent(calculatedStatus);

        logger.debug("Проверяем, работает ли котел");
        if (GasBoilerStatus.WORKS != gasBoilerService.getStatus()) {
            logger.debug("Котел не работает, операций с клапаном теплого пола не производим");
            return;
        }

        logger.debug("Запускаем расчет процента открытия клапана");
        Float openForDirectPercent = calculateOpenForDirectPercent(averageInternalTemperature, outsideTemperature);

        if (openForDirectPercent == null) {
            logger.warn("Расчет процента открытия клапана не удался");
            publishFloorHeatingErrorEvent();
            return;
        }

        logger.debug("Выставляем клапан");
        setValveOnPercent(openForDirectPercent);

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
            logger.warn("Не удалось рассчитать среднюю температуру");
            return null;
        } else {
            float sum = 0;
            for (Float temperature : polledTemperatures) {
                sum = sum + temperature;
            }
            return sum / polledTemperatures.size();
        }
    }

    private @Nullable Float calculateOpenForDirectPercent(float averageInternalTemperature, float outsideTemperature) {
        float targetDirectTemperature =
            calculateTargetDirectTemperature(averageInternalTemperature, outsideTemperature);

        logger.debug("Получаем температуры подачи из котла и обратки из полов");
        Float gasBoilerDirectTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE);
        Float floorReturnTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE);

        if (gasBoilerDirectTemperature == null || floorReturnTemperature == null) {
            logger.warn("Нет данных по необходимым температурам, не получается управлять трехходовым клапаном");
            return null;
        }

        logger.debug("Рассчитываем на какой процент должен быть открыт клапан для подачи из котла");

        if (floorReturnTemperature > targetDirectTemperature || floorReturnTemperature > gasBoilerDirectTemperature) {
            logger.info("Слишком горячая обратка из пола, закрываем клапан");
            return 0F;
        }

        if (targetDirectTemperature > gasBoilerDirectTemperature) {
            logger.info("Слишком холодная подача из котла, открываем клапан полностью");
            return 100F;
        }

        return 100 * (targetDirectTemperature - floorReturnTemperature) / (gasBoilerDirectTemperature
            - floorReturnTemperature);
    }

    private float calculateTargetDirectTemperature(float averageInternalTemperature, float outsideTemperature) {
        logger.debug("Запущена задача расчета целевой температуры подачи в полы");
        /* Формула расчета : (Tцелевая -Tнаруж)*K + Tцелевая + (Тцелевая-Твпомещении) */
        float calculated =
            (generalConfiguration.getTargetTemperature() - outsideTemperature) * temperatureConfiguration.getK()
                + generalConfiguration.getTargetTemperature() + (generalConfiguration.getTargetTemperature()
                - averageInternalTemperature);

        if (calculated < temperatureConfiguration.getDirectMinTemperature()) {
            logger.debug(
                "Целевая температура подачи в полы меньше минимальной, возвращаем минимальную - {}",
                temperatureConfiguration.getDirectMinTemperature()
            );
            return temperatureConfiguration.getDirectMinTemperature();
        } else if (calculated > temperatureConfiguration.getDirectMaxTemperature()) {
            logger.debug(
                "Целевая температура подачи в полы больше максимальной, возвращаем максимальную - {}",
                temperatureConfiguration.getDirectMaxTemperature()
            );
            return temperatureConfiguration.getDirectMaxTemperature();
        } else {
            logger.debug("Целевая температура подачи в полы - {}", calculated);
            return calculated;
        }
    }

    private void setValveOnPercent(Float openForDirectPercent) {
        try {
            logger.debug("Включаем питание сервопривода клапана");
            modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), true);

            //TODO какой-то метод в modbus сервисе, но документации пока нет

            Thread.sleep(relayConfiguration.getDelay() * 1000);
            logger.debug("Выключаем питание сервопривода клапана");
            modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), true);
        } catch (ModbusException | InterruptedException e) {
            logger.error("Ошибка выставления напряжение на ШИМ");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
        }
    }

    private void publishCalculatedEvent(FloorHeatingStatus status) {
        FloorHeatingStatusCalculatedEvent event = new FloorHeatingStatusCalculatedEvent(this, status);
        applicationEventPublisher.publishEvent(event);
    }

    private void publishFloorHeatingErrorEvent() {
        applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
    }

    @Override
    public FloorHeatingStatus getStatus() {
        return calculatedStatus;
    }

    @Override
    public String getFormattedStatus() {
        return calculatedStatus.getTemplate();
    }
}

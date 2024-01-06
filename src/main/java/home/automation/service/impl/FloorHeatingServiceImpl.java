package home.automation.service.impl;

import java.util.HashSet;
import java.util.Set;

import home.automation.configuration.FloorHeatingTemperatureConfiguration;
import home.automation.configuration.FloorHeatingValveDacConfiguration;
import home.automation.configuration.FloorHeatingValveRelayConfiguration;
import home.automation.configuration.GeneralConfiguration;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.FloorHeatingErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.FloorHeatingService;
import home.automation.service.GasBoilerService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FloorHeatingServiceImpl implements FloorHeatingService {

    private static final Logger logger = LoggerFactory.getLogger(FloorHeatingServiceImpl.class);

    private final Set<TemperatureSensor> averageInternalSensors = Set.of(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE);

    private final FloorHeatingTemperatureConfiguration temperatureConfiguration;

    private final FloorHeatingValveRelayConfiguration relayConfiguration;

    private final FloorHeatingValveDacConfiguration dacConfiguration;

    private final GeneralConfiguration generalConfiguration;

    private final TemperatureSensorsService temperatureSensorsService;

    private final GasBoilerService gasBoilerService;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    public FloorHeatingServiceImpl(
        FloorHeatingTemperatureConfiguration temperatureConfiguration,
        FloorHeatingValveRelayConfiguration relayConfiguration,
        FloorHeatingValveDacConfiguration dacConfiguration,
        GeneralConfiguration generalConfiguration,
        TemperatureSensorsService temperatureSensorsService,
        @Lazy GasBoilerService gasBoilerService,
        ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher,
        MeterRegistry meterRegistry
    ) {
        this.temperatureConfiguration = temperatureConfiguration;
        this.relayConfiguration = relayConfiguration;
        this.dacConfiguration = dacConfiguration;
        this.generalConfiguration = generalConfiguration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.gasBoilerService = gasBoilerService;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;

        Gauge.builder("floor", this::calculateTargetDirectTemperature)
            .tag("component", "target_temperature")
            .tag("system", "home_automation")
            .description("Расчетная температура подачи в теплые полы")
            .register(meterRegistry);

        Gauge.builder("floor", this::calculateOpenForDirectPercent)
            .tag("component", "target_valve_percent")
            .tag("system", "home_automation")
            .description("Расчетный процент открытия клапана")
            .register(meterRegistry);

        Gauge.builder("floor", this::getCurrentValvePercent)
            .tag("component", "current_valve_percent")
            .tag("system", "home_automation")
            .description("Текущий процент открытия клапана")
            .register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${floorHeating.controlInterval}")
    private void control() {
        logger.debug("Запущена джоба управления теплым полом");

        logger.debug("Проверяем, работает ли котел");
        if (gasBoilerService.getStatus() != GasBoilerStatus.WORKS) {
            logger.debug("Котел не работает, операций с клапаном теплого пола не производим");
            return;
        }

        logger.debug("Запускаем расчет процента открытия клапана");
        Integer openForDirectPercent = calculateOpenForDirectPercent();

        if (openForDirectPercent == null) {
            logger.warn("Расчет процента открытия клапана не удался");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
            return;
        }

        logger.debug("Выставляем клапан");
        setValveOnPercent(openForDirectPercent);
    }

    private @Nullable Integer calculateOpenForDirectPercent() {
        logger.debug("Получаем температуры подачи до подмеса и обратки из полов");
        Float floorDirectBeforeMixingTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_FLOOR_TEMPERATURE_BEFORE_MIXING);
        Float floorReturnTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE);

        logger.debug("Рассчитываем целевую температуру подачи в полы");
        Float targetDirectTemperature = calculateTargetDirectTemperature();

        if (floorDirectBeforeMixingTemperature == null || floorReturnTemperature == null || targetDirectTemperature == null) {
            logger.warn("Нет данных по необходимым температурам, не получается управлять трехходовым клапаном");
            return null;
        }

        logger.debug("Рассчитываем на какой процент должен быть открыт клапан для подачи");

        if (floorReturnTemperature > targetDirectTemperature
            || floorReturnTemperature > floorDirectBeforeMixingTemperature) {
            logger.info("Слишком горячая обратка из пола, закрываем клапан");
            return 0;
        }

        if (targetDirectTemperature > floorDirectBeforeMixingTemperature) {
            logger.info("Слишком холодная подача из котла, открываем клапан полностью");
            return 100;
        }

        return Math.round(100 * (targetDirectTemperature - floorReturnTemperature) / (floorDirectBeforeMixingTemperature
            - floorReturnTemperature));
    }

    private @Nullable Float calculateTargetDirectTemperature() {
        logger.debug("Запущена задача расчета целевой температуры подачи в полы");

        Float averageInternalTemperature = calculateAverageInternalTemperature();
        if (averageInternalTemperature == null) {
            logger.warn("Нет возможности определить среднюю температуру в помещениях");
            return null;
        }

        Float outsideTemperature = temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);
        if (outsideTemperature == null) {
            logger.warn("Нет возможности определить температуру на улице");
            return null;
        }

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

    private void setValveOnPercent(int openForDirectPercent) {
        try {
            logger.debug("Проверяем текущий процент открытия клапана");
            float currentVoltageInV = (float) modbusService.readHoldingRegister(dacConfiguration.getAddress(), dacConfiguration.getRegister()) / 100;
            logger.debug("Текущее напряжение на ЦАП {}V", currentVoltageInV);
            int currentPercent = getPercentFromVoltageInV(currentVoltageInV);

            if (Math.abs(currentPercent - openForDirectPercent) < dacConfiguration.getAccuracy()) {
                logger.debug("Клапан установлен в пределах погрешности");
                return;
            }

            logger.debug("Включаем питание сервопривода клапана");
            modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), true);

            logger.debug("Подаем управляющее напряжение, оно в десятках милливольт");
            int voltageIn10mv = Math.round(getVoltageInVFromPercent(openForDirectPercent) * 100);
            logger.debug("Устанавливаемое напряжение на ЦАП {}V", voltageIn10mv / 100);
            modbusService.writeHoldingRegister(dacConfiguration.getAddress(), dacConfiguration.getRegister(), voltageIn10mv);

            Thread.sleep(relayConfiguration.getDelay() * 1000);
            logger.debug("Выключаем питание сервопривода клапана");
            modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), false);

            logger.info("Сервопривод был передвинут, новый процент открытия {}", openForDirectPercent);
        } catch (ModbusException | InterruptedException e) {
            logger.error("Ошибка выставления напряжение на ЦАП");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
        }
    }

    private Integer getCurrentValvePercent() {
        try {
            logger.debug("Проверяем текущий процент открытия клапана");
            float currentVoltageInV = (float) modbusService.readHoldingRegister(dacConfiguration.getAddress(), dacConfiguration.getRegister()) / 100;
            logger.debug("Текущее напряжение на ЦАП {}V", currentVoltageInV);
            return getPercentFromVoltageInV(currentVoltageInV);
        } catch (ModbusException e) {
            logger.error("Ошибка чтения напряжение на ЦАП");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
        }
        return null;
    }

    private int getPercentFromVoltageInV(float voltage) {
        /* процент считается в интервале напряжений от 2 до 10 В */
        if (voltage < 2) {
            voltage = 2;
        }
        if (voltage > 10) {
            voltage = 10;
        }
        return Math.round(((voltage - 2) / 8) * 100);
    }

    private float getVoltageInVFromPercent(int percent) {
        /* процент считается в интервале напряжений от 2 до 10 В */
        return 2f + 8f * percent / 100;
    }
}

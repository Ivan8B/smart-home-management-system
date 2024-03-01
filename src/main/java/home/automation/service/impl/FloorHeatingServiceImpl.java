package home.automation.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import home.automation.configuration.FloorHeatingTemperatureConfiguration;
import home.automation.configuration.FloorHeatingValveDacConfiguration;
import home.automation.configuration.FloorHeatingValveRelayConfiguration;
import home.automation.configuration.GeneralConfiguration;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.FloorHeatingErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.FloorHeatingService;
import home.automation.service.HistoryService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
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

    private final HistoryService historyService;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    Environment environment;

    private final ReentrantLock valveLocker = new ReentrantLock();

    public FloorHeatingServiceImpl(
        FloorHeatingTemperatureConfiguration temperatureConfiguration,
        FloorHeatingValveRelayConfiguration relayConfiguration,
        FloorHeatingValveDacConfiguration dacConfiguration,
        GeneralConfiguration generalConfiguration,
        TemperatureSensorsService temperatureSensorsService,
        HistoryService historyService,
        ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher,
        Environment environment,
        MeterRegistry meterRegistry
    ) {
        this.temperatureConfiguration = temperatureConfiguration;
        this.relayConfiguration = relayConfiguration;
        this.dacConfiguration = dacConfiguration;
        this.generalConfiguration = generalConfiguration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.historyService = historyService;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.environment = environment;

        Gauge.builder("floor", this::calculateTargetDirectTemperature)
            .tag("component", "target_direct_temperature")
            .tag("system", "home_automation")
            .description("Расчетная температура подачи в теплые полы")
            .register(meterRegistry);

        Gauge.builder("floor", this::getCurrentValvePercent)
            .tag("component", "current_valve_percent")
            .tag("system", "home_automation")
            .description("Текущий процент открытия клапана по напряжению")
            .register(meterRegistry);

        Gauge.builder("floor", this::getEffectiveValvePercent)
            .tag("component", "effective_valve_percent")
            .tag("system", "home_automation")
            .description("Текущий процент открытия клапана по температуре")
            .register(meterRegistry);
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() {
        if (!environment.matchesProfiles("test")) {
            logger.debug("Чтобы отпустить процесс инициализации приложения выставляем клапан через таски");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            logger.debug("Система была перезагружена, закрываем клапан подмеса для калибровки");
            executor.submit(() -> setValveOnPercent(-1));
            logger.debug("и открываем его на половину");
            executor.submit(() -> setValveOnPercent(50));
        }
    }

    @Scheduled(fixedRateString = "${floorHeating.controlInterval}")
    private void control() {
        logger.debug("Запущена джоба управления теплым полом");

        logger.debug("Рассчитываем целевую температуру подачи в теплые полы");
        Float targetDirectTemperature =  calculateTargetDirectTemperature();
        if (targetDirectTemperature == null) {
            logger.warn("Нет данных по целевой подаче в полы, не получается управлять трехходовым клапаном");
            return;
        }
        Integer calculatedTargetValvePercent = calculateTargetValvePercentByTemperatureBeforeMixing(targetDirectTemperature);
        if (calculatedTargetValvePercent == null) {
            logger.warn("Не удалось рассчитать целевое положение клапана, не получается управлять трехходовым клапаном");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
            return;
        }
        logger.debug("Добавляем рассчитанное значение клапана в историю");
        historyService.putCalculatedTargetValvePercent(calculatedTargetValvePercent, Instant.now());

        logger.debug("Рассчитываем средний целевой процент за последний час");
        Integer averageTargetValvePercent = historyService.getAverageCalculatedTargetValvePercentForLastHour();
        if (averageTargetValvePercent == null) {
            logger.warn("Не удалось рассчитать среднее целевое положение клапана, не получается управлять трехходовым клапаном");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
            return;
        }

        logger.debug("Проверяем насколько текущая температура подачи отличается от целевой");
        Float currentDirectAfterMixingTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_FLOOR_TEMPERATURE_AFTER_MIXING);
        if (currentDirectAfterMixingTemperature == null) {
            logger.warn("Нет данных по текущей температуре, не производим операций с клапаном");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
            return;
        }

        logger.debug("Проверяем требуется ли коррекция положения клапана");
        float delta = targetDirectTemperature - currentDirectAfterMixingTemperature;
        if (Math.abs(delta) <= temperatureConfiguration.getAccuracy()) {
            logger.debug("Операций с клапаном теплого пола не требуется");
            return;
        }

        logger.debug("Выставляем клапан (если нет блокировки). Если заблокирован - установим при следующей попытке");
        if (valveLocker.isLocked()) {
            logger.info("Клапан заблокирован, не выставляем положение");
            return;
        }
        setValveOnPercent(averageTargetValvePercent);
    }

    private Integer calculateTargetValvePercentByTemperatureBeforeMixing(float targetDirectTemperature) {
        logger.debug("Рассчитываем целевое положение клапана");

        logger.debug("Получаем температуру подачи в узел подмеса");
        Float floorDirectBeforeMixingTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_FLOOR_TEMPERATURE_BEFORE_MIXING);
        if (floorDirectBeforeMixingTemperature == null) {
            logger.warn("Нет данных по температуре подачи в узел подмеса, не получается управлять трехходовым клапаном");
            return null;
        }

        logger.debug("Получаем температуру обратки из полов");
        Float floorReturnTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE);
        if (floorReturnTemperature == null) {
            logger.warn("Нет данных по температуре обратки из пола, не получается управлять трехходовым клапаном");
            return null;
        }

        logger.debug("Положение клапана считаем по текущей подаче в узел подмеса");
        return calculateTargetValvePercent(targetDirectTemperature, floorDirectBeforeMixingTemperature, floorReturnTemperature);
    }

    @Nullable
    Float calculateTargetDirectTemperature() {
        logger.debug("Запущена задача расчета целевой температуры подачи в полы");
        float calculated;

        Float averageInternalTemperature = calculateAverageInternalTemperature();
        if (averageInternalTemperature == null) {
            logger.warn("Нет возможности определить среднюю температуру в помещениях");
            return null;
        }

        Float outsideTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);
        if (outsideTemperature == null) {
            logger.warn("Нет возможности определить температуру на улице");
            return null;
        }

        Float returnTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE);
        if (returnTemperature == null) {
            logger.warn("Нет возможности определить температуру обратки из теплых полов");
            return null;
        }

        logger.debug("Проверяем граничные условия");
        if (generalConfiguration.getInsideTarget() < outsideTemperature || generalConfiguration.getInsideTarget() < averageInternalTemperature) {
            logger.debug("Нарушены граничные условия, возвращаем минимальную температуру подачи в полы");
            return temperatureConfiguration.getDirectMinTemperature();
        } else {
            /* Формула расчета : (Tцелевая -Tнаруж)*K + Tцелевая + (Тцелевая-Твпомещении) */
            calculated =
                (generalConfiguration.getInsideTarget() - outsideTemperature) * temperatureConfiguration.getK()
                    + generalConfiguration.getInsideTarget() + (generalConfiguration.getInsideTarget()
                    - averageInternalTemperature);
        }

        if (calculated > temperatureConfiguration.getDirectMaxTemperature()) {
            logger.debug(
                "Целевая температура подачи в полы больше максимальной, возвращаем максимальную - {}",
                temperatureConfiguration.getDirectMaxTemperature()
            );
            return temperatureConfiguration.getDirectMaxTemperature();
        }

        if (calculated < temperatureConfiguration.getDirectMinTemperature()) {
            logger.debug(
                "Целевая температура подачи в полы меньше минимальной, возвращаем минимальную - {}",
                temperatureConfiguration.getDirectMinTemperature()
            );
            return temperatureConfiguration.getDirectMinTemperature();
        }

        logger.debug("Целевая температура подачи в полы - {}", calculated);
        return calculated;
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

    private int calculateTargetValvePercent(float targetDirectTemperature, float floorDirectBeforeMixingTemperature, float floorReturnTemperature) {
        logger.debug("Проверяем граничные условия");
        if (targetDirectTemperature < floorReturnTemperature || floorDirectBeforeMixingTemperature < floorReturnTemperature) {
            logger.debug("Слишком высокая температура обратки из теплых полов, прикрываем клапан до минимального");
            return 0;
        }

        int targetPercent = Math.round(
            100 * (targetDirectTemperature - floorReturnTemperature) / (floorDirectBeforeMixingTemperature
                - floorReturnTemperature));

        if (targetPercent > 100) {
            logger.debug("Слишком высокий процент открытия клапана {}, выставляем максимально допустимый {}", targetPercent, 100);
            return 100;
        }
        if (targetPercent < 0) {
            logger.debug("Слишком низкий процент открытия клапана {}, выставляем минимально допустимый {}", targetPercent, 0);
            return 0;
        }
        return targetPercent;
    }

    private void setValveOnPercent(int targetValvePercent) {
        valveLocker.lock();
        try {
            logger.debug("Рассчитываем на сколько секунд подавать питание");
            int powerTime;
            logger.debug("Если выставляем -1 для калибровки клапана - всегда подаем питание на максимальное время");
            if (targetValvePercent == -1) {
                powerTime = relayConfiguration.getRotationTime() + relayConfiguration.getRotationTimeReserve();
            } else {
                logger.debug("Считываем текущий процент открытия клапана");
                Integer currentValvePercent = getCurrentValvePercent();
                if (currentValvePercent == null) {
                    logger.warn("Не удалось получить текущее положение клапана");
                    applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
                    return;
                }
                int valvePercentDelta = targetValvePercent - currentValvePercent;
                if (Math.abs(valvePercentDelta) < dacConfiguration.getAccuracy()) {
                    logger.debug("Клапан уже установлен на заданный процент {}", targetValvePercent);
                    return;
                }
                powerTime = calculateTime(targetValvePercent, currentValvePercent);
            }

            logger.info("Включаем питание сервопривода клапана");
            modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), true);

            logger.debug("Подаем управляющее напряжение, оно в десятках милливольт");
            int voltageIn10mv = Math.round(getVoltageInVFromPercent(targetValvePercent) * 100);
            logger.debug("Устанавливаемое напряжение на ЦАП {}V", (float) voltageIn10mv / 100);
            modbusService.writeHoldingRegister(
                dacConfiguration.getAddress(),
                dacConfiguration.getRegister(),
                voltageIn10mv
            );

            Thread.sleep(powerTime * 1000);
            logger.info("Выключаем питание сервопривода клапана");
            modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), false);

            logger.info("Сервопривод был передвинут, новый процент открытия {}", targetValvePercent);
        } catch (ModbusException | InterruptedException e) {
            logger.error("Ошибка выставления напряжение на ЦАП");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
        } finally {
            valveLocker.unlock();
        }
    }

    private int calculateTime(int targetValvePercent, int currentValvePercent) {
        /* когда клапан крутится по часовой стрелке (то есть уменьшает процент открытия) - он доходит до нуля и возвращается до нужного процента */
        /* но на всякий случая подаем питание пропорционально сумме положений и при увеличении процента открытия тоже, похоже иногда клапан увеличивает процент через ноль */
        /* считаем по напряжению которое будет выдаваться на ЦАП, вычитаем 2 раза по 2 вольта - клапан работает от 2 до 10V */
        return (int) (Math.round(
            relayConfiguration.getRotationTime()
                * (getVoltageInVFromPercent(currentValvePercent) + getVoltageInVFromPercent(targetValvePercent) - 4) / 8.0)
                    + 2 * relayConfiguration.getRotationTimeReserve());
    }

    private Integer getCurrentValvePercent() {
        try {
            logger.debug("Проверяем текущий процент открытия клапана");
            float currentVoltageInV =
                (float) modbusService.readHoldingRegister(dacConfiguration.getAddress(), dacConfiguration.getRegister())
                    / 100;
            logger.debug("Текущее напряжение на ЦАП {}V", currentVoltageInV);
            return getPercentFromVoltageInV(currentVoltageInV);
        } catch (ModbusException e) {
            logger.error("Ошибка чтения напряжение на ЦАП");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
        }
        return null;
    }

    private Integer getEffectiveValvePercent() {
        Float floorDirectBeforeMixingTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_FLOOR_TEMPERATURE_BEFORE_MIXING);
        Float floorDirectAfterMixingTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_FLOOR_TEMPERATURE_AFTER_MIXING);
        Float floorReturnTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE);
        if (floorDirectBeforeMixingTemperature == null || floorDirectAfterMixingTemperature == null || floorReturnTemperature == null) {
            return null;
        }
        int calculated =  Math.round(
            100 * (floorDirectAfterMixingTemperature - floorReturnTemperature) / (floorDirectBeforeMixingTemperature
                - floorReturnTemperature));
        if (calculated < 0 || calculated > 100) {
            return -1;
        }
        return calculated;
    }

    private int getPercentFromVoltageInV(float voltage) {
        /* процент считается в интервале напряжений от 2 до 10 В */
        if (voltage < 2) {
            voltage = 2;
        }
        if (voltage > 10) {
            voltage = 10;
        }
        int correctedPercent = Math.round(((voltage - 2) / 8) * 100);
        if (correctedPercent == 0) {
            return 0;
        }
        /* компенсируем коррекцию */
        return (int) Math.round((correctedPercent - 25) / 0.5);
    }

    private float getVoltageInVFromPercent(int percent) {
        /* поскольку клапан открывается неравномерно нужна коррекция */
        /* эта коррекция по линейной функции и весьма приблизительна */
        /* если процент открытия -1 - корректировать не надо, нужно вернуть 0 для калибровки клапана */
        float correctedPercent = (percent == -1) ? 0 : (float) (25 + 0.5 * percent);

        /* клапан работает в интервале напряжений от 2 до 10 В */
        return 2f + 8f * correctedPercent / 100;
    }

    @Override
    public String getFormattedStatus() {
        return "процент подмеса в теплые полы - " + getCurrentValvePercent() + "%";
    }
}

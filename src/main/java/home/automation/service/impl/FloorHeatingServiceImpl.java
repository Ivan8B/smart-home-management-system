package home.automation.service.impl;

import home.automation.configuration.FloorHeatingConfiguration;
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

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class FloorHeatingServiceImpl implements FloorHeatingService {
    private static final Logger logger = LoggerFactory.getLogger(FloorHeatingServiceImpl.class);
    private final Set<TemperatureSensor> averageInternalSensors = Set.of(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE);
    private final FloorHeatingConfiguration floorHeatingConfiguration;
    private final FloorHeatingTemperatureConfiguration temperatureConfiguration;
    private final FloorHeatingValveRelayConfiguration relayConfiguration;
    private final FloorHeatingValveDacConfiguration dacConfiguration;
    private final GeneralConfiguration generalConfiguration;
    private final GasBoilerService gasBoilerService;
    private final TemperatureSensorsService temperatureSensorsService;
    private final HistoryService historyService;
    private final ModbusService modbusService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ReentrantLock valveLocker = new ReentrantLock();
    Environment environment;

    public FloorHeatingServiceImpl(
            FloorHeatingConfiguration floorHeatingConfiguration,
            FloorHeatingTemperatureConfiguration temperatureConfiguration,
            FloorHeatingValveRelayConfiguration relayConfiguration,
            FloorHeatingValveDacConfiguration dacConfiguration,
            GeneralConfiguration generalConfiguration,
            GasBoilerService gasBoilerService,
            TemperatureSensorsService temperatureSensorsService,
            HistoryService historyService,
            ModbusService modbusService,
            ApplicationEventPublisher applicationEventPublisher,
            Environment environment,
            MeterRegistry meterRegistry
    ) {
        this.floorHeatingConfiguration = floorHeatingConfiguration;
        this.temperatureConfiguration = temperatureConfiguration;
        this.relayConfiguration = relayConfiguration;
        this.dacConfiguration = dacConfiguration;
        this.generalConfiguration = generalConfiguration;
        this.gasBoilerService = gasBoilerService;
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

        Gauge.builder("floor", this::getCalculatedValvePercent)
                .tag("component", "calculated_valve_percent")
                .tag("system", "home_automation")
                .description("Рассчитанный моментальный процент открытия клапана")
                .register(meterRegistry);
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() {
        if (!environment.matchesProfiles("test")) {
            logger.debug("Чтобы отпустить процесс инициализации приложения выставляем клапан через таски");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            logger.info("Система была перезагружена, закрываем клапан подмеса для калибровки");
            executor.submit(() -> setValveOnPercent(-1));
            logger.info("и открываем его на половину");
            executor.submit(() -> setValveOnPercent(50));
        }
    }

    @Scheduled(fixedRateString = "${floorHeating.controlInterval}")
    private void control() {
        logger.debug("Запущена задача управления теплым полом");

        if (gasBoilerService.getStatus() != GasBoilerStatus.WORKS) {
            logger.info("Котел не работает на отопление, теплым полом управлять не нужно");
            return;
        }

        Float targetDirectTemperature = calculateTargetDirectTemperature();
        logger.debug("Целевая температура подачи в полы - {}", targetDirectTemperature);
        if (targetDirectTemperature == null) {
            logger.warn("Нет данных по целевой подаче в полы, не получится управлять трехходовым клапаном");
            return;
        }
        Integer calculatedTargetValvePercent = calculateTargetValvePercentByTemperatureBeforeMixing(
                targetDirectTemperature);
        logger.debug("Рассчитанный по температуре подмеса целевой процент открытия клапана {}",
                calculatedTargetValvePercent);
        if (calculatedTargetValvePercent == null) {
            logger.warn("Не удалось рассчитать целевое положение клапана, не получится управлять трехходовым " +
                    "клапаном");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
            return;
        }
        logger.debug("Добавляем рассчитанное значение клапана {} в историю", calculatedTargetValvePercent);
        historyService.putCalculatedTargetValvePercent(calculatedTargetValvePercent, Instant.now());

        Integer averageTargetValvePercent = historyService.getAverageCalculatedTargetValvePercentForLastNValues();
        logger.debug("Целевой процент за последние " + floorHeatingConfiguration.getValuesCountForAverage() +
                " расчетов {}", averageTargetValvePercent);
        if (averageTargetValvePercent == null) {
            logger.info(
                    "Недостаточно данных по  среднему целевому положению клапана, не получится управлять трехходовым" +
                            " клапаном");
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
        Float floorDirectBeforeMixingTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_FLOOR_TEMPERATURE_BEFORE_MIXING);
        logger.debug("Температура подачи в узел подмеса {}", floorDirectBeforeMixingTemperature);
        if (floorDirectBeforeMixingTemperature == null) {
            logger.warn("Нет данных по температуре подачи в узел подмеса, не получится управлять трехходовым " +
                    "клапаном");
            return null;
        }

        Float floorReturnTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE);
        logger.debug("Температура обратки из полов {}", floorReturnTemperature);
        if (floorReturnTemperature == null) {
            logger.warn("Нет данных по температуре обратки из пола, не получится управлять трехходовым клапаном");
            return null;
        }

        if (targetDirectTemperature < floorReturnTemperature ||
                floorDirectBeforeMixingTemperature < floorReturnTemperature) {
            logger.debug("Слишком высокая температура обратки из теплых полов, прикрываем клапан до минимального");
            return 0;
        }

        int targetPercent = Math.round(
                100 * (targetDirectTemperature - floorReturnTemperature) / (floorDirectBeforeMixingTemperature
                        - floorReturnTemperature));

        if (targetPercent > 100) {
            logger.debug("Слишком высокий процент открытия клапана {}, возвращаем максимально допустимый {}",
                    targetPercent,
                    100);
            return 100;
        }
        if (targetPercent < 0) {
            logger.debug("Слишком низкий процент открытия клапана {}, возвращаем минимально допустимый {}",
                    targetPercent,
                    0);
            return 0;
        }

        logger.debug("Рассчитанный целевой процент открытия клапана {}", targetPercent);
        return targetPercent;
    }

    @Nullable
    Float calculateTargetDirectTemperature() {
        float calculated;

        Float averageInternalTemperature = calculateAverageInternalTemperature();
        logger.debug("Средняя температура в помещениях {}", averageInternalTemperature);
        if (averageInternalTemperature == null) {
            logger.warn("Нет возможности определить среднюю температуру в помещениях");
            return null;
        }

        Float outsideTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);
        logger.debug("Температура на улице {}", outsideTemperature);
        if (outsideTemperature == null) {
            logger.warn("Нет возможности определить температуру на улице");
            return null;
        }

        Float returnTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE);
        logger.debug("Температура обратки из полов {}", returnTemperature);
        if (returnTemperature == null) {
            logger.warn("Нет возможности определить температуру обратки из теплых полов");
            return null;
        }

        if (generalConfiguration.getInsideTarget() < outsideTemperature ||
                generalConfiguration.getInsideTarget() < averageInternalTemperature) {
            logger.debug("Нарушены граничные условия, возвращаем минимальную температуру подачи в полы");
            return temperatureConfiguration.getDirectMinTemperature();
        }
        else {
            /* Формула расчета : (Tцелевая - Tнаруж)*K + Tцелевая + (Тцелевая - Твпомещении) + const(из настроек) */
            calculated =
                    (generalConfiguration.getInsideTarget() - outsideTemperature) * temperatureConfiguration.getK()
                            + generalConfiguration.getInsideTarget() + (generalConfiguration.getInsideTarget()
                            - averageInternalTemperature) + temperatureConfiguration.getDirectConstTemperature();
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

    private void setValveOnPercent(int targetValvePercent) {
        valveLocker.lock();
        try {
            Integer currentValvePercent = getCurrentValvePercent();
            logger.debug("Текущий процент открытия клапана {}", currentValvePercent);
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

            logger.info("Включаем питание сервопривода клапана");
            modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), true);

            int voltageIn10mv = Math.round(getVoltageInVFromPercent(targetValvePercent) * 100);
            logger.debug("Устанавливаемое напряжение на ЦАП {}V", (float) voltageIn10mv / 100);
            modbusService.writeHoldingRegister(
                    dacConfiguration.getAddress(),
                    dacConfiguration.getRegister(),
                    voltageIn10mv
            );

            Thread.sleep(relayConfiguration.getRotationTime() * 1000L);
            logger.info("Выключаем питание сервопривода клапана");
            modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), false);

            logger.info("Сервопривод был передвинут, новый процент открытия {}", targetValvePercent);
        } catch (ModbusException | InterruptedException e) {
            logger.error("Ошибка выставления напряжение на ЦАП или работы с реле питания");
            applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
        } finally {
            valveLocker.unlock();
        }
    }

    private Integer getCurrentValvePercent() {
        try {
            logger.debug("Проверяем текущий процент открытия клапана");
            float currentVoltageInV =
                    (float) modbusService.readHoldingRegister(dacConfiguration.getAddress(),
                            dacConfiguration.getRegister())
                            / 100;
            logger.debug("Текущее напряжение на ЦАП {}V", currentVoltageInV);
            int currentPercent = getPercentFromVoltageInV(currentVoltageInV);
            logger.debug("Текущий процент открытия клапана по ЦАП {}", currentPercent);
            return currentPercent;
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
        if (floorDirectBeforeMixingTemperature == null ||
                floorDirectAfterMixingTemperature == null ||
                floorReturnTemperature == null) {
            return null;
        }
        int calculated = Math.round(
                100 * (floorDirectAfterMixingTemperature - floorReturnTemperature) / (floorDirectBeforeMixingTemperature
                        - floorReturnTemperature));
        if (calculated < 0 || calculated > 100) {
            return -1;
        }
        logger.debug("Текущий процент открытия клапана по температуре {}", calculated);
        return calculated;
    }

    private Integer getCalculatedValvePercent() {
        if (gasBoilerService.getStatus() != GasBoilerStatus.WORKS) {
            return null;
        }
        return historyService.getLastCalculatedTargetValvePercent();
    }

    private int getPercentFromVoltageInV(float voltage) {
        /* процент считается в интервале напряжений от 2 до 10 В */
        if (voltage < 2) {
            voltage = 2;
        }
        if (voltage > 10) {
            voltage = 10;
        }
        /* клапан работает в интервале напряжений от 2 до 10 В */
        int correctedPercent = Math.round(((voltage - 2) / 8) * 100);
        if (correctedPercent == 0) {
            return 0;
        }
        /* компенсируем коррекцию */
        return Math.round((correctedPercent - dacConfiguration.getCorrectionConstant()) / dacConfiguration.getCorrectionGradient());
    }

    private float getVoltageInVFromPercent(int percent) {
        /* поскольку клапан открывается неравномерно нужна коррекция */
        /* эта коррекция по линейной функции и весьма приблизительна */
        /* если процент открытия -1 - корректировать не надо, нужно вернуть 0 для калибровки клапана */
        float correctedPercent = (percent == -1) ? 0 : (dacConfiguration.getCorrectionGradient() * percent + dacConfiguration.getCorrectionConstant());

        /* клапан работает в интервале напряжений от 2 до 10 В */
        return 2f + 8f * correctedPercent / 100;
    }

    @Override
    public String getFormattedStatus() {
        Float targetDirectTemperature = calculateTargetDirectTemperature();
        String formattedTargetDirectTemperature;
        if (targetDirectTemperature == null) {
            formattedTargetDirectTemperature=  "ошибка расчета!";
        } else {
            DecimalFormat df = new DecimalFormat("#.#");
            formattedTargetDirectTemperature = df.format(targetDirectTemperature) + " C°";
        }

        return "текущий процент подмеса в теплые полы - " + getCurrentValvePercent() + "%" + "\n* " +
                "целевая температура подачи в теплые полы " + formattedTargetDirectTemperature;
    }
}

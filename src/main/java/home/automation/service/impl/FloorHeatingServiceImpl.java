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
import home.automation.utils.P_F;
import home.automation.utils.decimal.TD_F;
import home.automation.utils.decimal.VD_F;
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
    private Instant lastRotateTime;

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
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() {
        if (!environment.matchesProfiles("test")) {
            logger.debug("Чтобы отпустить процесс инициализации приложения выставляем клапан через таски");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            logger.info("Система была перезагружена, закрываем клапан подмеса для калибровки");
            executor.submit(() -> setValveOnPercent(-1));
            logger.info("и открываем его на треть");
            executor.submit(() -> setValveOnPercent(33));
        }
    }

    @Scheduled(fixedRateString = "${floorHeating.controlInterval}")
    private void control() {
        logger.debug("Запущена задача управления теплым полом");

        if (gasBoilerService.getStatus() == GasBoilerStatus.WORKS) {
            logger.debug("Котел работает на отопление, можно считать целевое положение клапана");

            Float targetDirectTemperature = calculateTargetDirectTemperature();
            logger.debug("Целевая температура подачи в полы - {}", TD_F.format(targetDirectTemperature));
            if (targetDirectTemperature == null) {
                logger.warn("Нет данных по целевой подаче в полы, не получится считать положение клапана");
                applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
                return;
            }
            Integer calculatedTargetValvePercent = calculateTargetValvePercentByTemperatureBeforeMixing(
                    targetDirectTemperature);
            logger.debug("Рассчитанный по температуре подмеса целевой процент открытия клапана {}",
                    P_F.format(calculatedTargetValvePercent));
            if (calculatedTargetValvePercent == null) {
                logger.warn("Не удалось рассчитать целевое положение клапана, не получится считать положение клапана");
                applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
                return;
            }
            logger.debug("Добавляем рассчитанное значение клапана {} в историю", P_F.format(calculatedTargetValvePercent));
            historyService.putCalculatedTargetValvePercent(calculatedTargetValvePercent, Instant.now());
        }

        if (gasBoilerService.getStatus() != GasBoilerStatus.WORKS
                || (historyService.getGasBoilerCurrentStatusDuration() != null &&
                historyService.getGasBoilerCurrentStatusDuration()
                        .compareTo(floorHeatingConfiguration.getGasBoilerWorkDurationToRotateValve()) > 0)) {
            logger.debug("Котел не работает на отопление или работает уже давно, можно управлять клапаном");

            Integer averageTargetValvePercent = historyService.getAverageCalculatedTargetValvePercentForLastNValues();
            logger.debug("Целевой процент за последние " + floorHeatingConfiguration.getValuesCountForAverage() +
                    " расчетов {}", P_F.format(averageTargetValvePercent));
            if (averageTargetValvePercent == null) {
                logger.info(
                        "Недостаточно данных по  среднему целевому положению клапана, не получится управлять трехходовым" +
                                " клапаном");
                return;
            }

            setValveOnPercent(averageTargetValvePercent);
            return;
        }

        if (gasBoilerService.getStatus() != GasBoilerStatus.WORKS
                && lastRotateTime.isBefore(Instant.now().minus(floorHeatingConfiguration.getIdleIntervalToRotate()))) {
            logger.debug("Клапаном в этом цикле не управляли, котел не работает, клапан проворачивался слишком давно");
            logger.info("Начинаем проворот клапана, полностью закрываем его");
            setValveOnPercent(-1);
            logger.info("и открываем его на треть");
            setValveOnPercent(33);
        }
    }

    private Integer calculateTargetValvePercentByTemperatureBeforeMixing(float targetDirectTemperature) {
        Float floorDirectBeforeMixingTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_FLOOR_TEMPERATURE_BEFORE_MIXING);
        logger.debug("Температура подачи в узел подмеса {}", TD_F.format(floorDirectBeforeMixingTemperature));
        if (floorDirectBeforeMixingTemperature == null) {
            logger.warn("Нет данных по температуре подачи в узел подмеса, не получится управлять трехходовым " +
                    "клапаном");
            return null;
        }

        Float floorReturnTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE);
        logger.debug("Температура обратки из полов {}", TD_F.format(floorReturnTemperature));
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
                    P_F.format(targetPercent),
                    P_F.format(100));
            return 100;
        }
        if (targetPercent < 0) {
            logger.debug("Слишком низкий процент открытия клапана {}, возвращаем минимально допустимый {}",
                    P_F.format(targetPercent),
                    P_F.format(0));
            return 0;
        }

        logger.debug("Рассчитанный целевой процент открытия клапана {}", P_F.format(targetPercent));
        return targetPercent;
    }

    @Nullable
    Float calculateTargetDirectTemperature() {
        float calculated;

        Float averageInternalTemperature = calculateAverageInternalTemperature();
        logger.debug("Средняя температура в помещениях {}", TD_F.format(averageInternalTemperature));
        if (averageInternalTemperature == null) {
            logger.warn("Нет возможности определить среднюю температуру в помещениях");
            return null;
        }

        Float outsideTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);
        logger.debug("Температура на улице {}", TD_F.format(outsideTemperature));
        if (outsideTemperature == null) {
            logger.warn("Нет возможности определить температуру на улице");
            return null;
        }

        if (generalConfiguration.getInsideTarget() < outsideTemperature) {
            logger.debug("На улице слишком жарко, возвращаем минимальную температуру подачи в полы");
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
                    TD_F.format(temperatureConfiguration.getDirectMaxTemperature())
            );
            return temperatureConfiguration.getDirectMaxTemperature();
        }

        if (calculated < temperatureConfiguration.getDirectMinTemperature()) {
            logger.debug(
                    "Целевая температура подачи в полы меньше минимальной, возвращаем минимальную - {}",
                    TD_F.format(temperatureConfiguration.getDirectMinTemperature())
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
            if (valveLocker.isLocked()) {
                logger.info("Клапан заблокирован, не выставляем положение");
                return;
            }
            int powerTime;
            if (targetValvePercent == -1) {
                logger.debug("Если выставляем -1 для калибровки клапана - всегда подаем питание на максимальное время");
                powerTime = relayConfiguration.getRotationTime() + relayConfiguration.getRotationTimeReserve();
            }
            else {
                Integer currentValvePercent = getCurrentValvePercent();
                logger.debug("Текущий процент открытия клапана {}", P_F.format(currentValvePercent));
                if (currentValvePercent == null) {
                    logger.warn("Не удалось получить текущее положение клапана");
                    applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
                    return;
                }
                int valvePercentDelta = targetValvePercent - currentValvePercent;
                if (Math.abs(valvePercentDelta) < dacConfiguration.getAccuracy()) {
                    logger.debug("Клапан уже установлен на заданный процент {}", P_F.format(targetValvePercent));
                    return;
                }
                /* считаем по напряжению которое будет выдаваться на ЦА */
                /* вычитаем из напряжения 2 вольта - клапан работает от 2 до 10V */
                /* клапан доходит до нуля и возвращается до нужного процента */
                powerTime =
                        (int) Math.round(relayConfiguration.getRotationTime() * ((getVoltageInVFromPercentWithCorrection(currentValvePercent) - 2)
                                + (getVoltageInVFromPercentWithCorrection(targetValvePercent) - 2)) / 8.0) + relayConfiguration.getRotationTimeReserve();
                logger.debug("Питание на клапан нужно подать на {} секунд", powerTime);
            }

            float voltage = getVoltageInVFromPercentWithCorrection(targetValvePercent);

            logger.debug("Берем блокировку на клапан");
            valveLocker.lock();
            try {
                logger.info("Включаем питание сервопривода клапана");
                modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), true);

                logger.debug("Устанавливаемое напряжение на ЦАП {}", VD_F.format(voltage));
                modbusService.writeHoldingRegister(
                        dacConfiguration.getAddress(),
                        dacConfiguration.getRegister(),
                        Math.round(voltage* 100)
                );

                Thread.sleep(powerTime * 1000L);
                logger.info("Выключаем питание сервопривода клапана");
                modbusService.writeCoil(relayConfiguration.getAddress(), relayConfiguration.getCoil(), false);
            } catch (ModbusException | InterruptedException e) {
                logger.error("Ошибка выставления напряжение на ЦАП или работы с реле питания");
                applicationEventPublisher.publishEvent(new FloorHeatingErrorEvent(this));
            } finally {
                logger.debug("Снимаем блокировку на клапан");
                valveLocker.unlock();
                logger.debug("Записываем время поворота клапана");
                lastRotateTime = Instant.now();
            }

            if (targetValvePercent == -1) {
                logger.info("Сервопривод полностью перекрыт");
            } else {
                logger.info("Сервопривод был передвинут за {} секунд, новый процент открытия {}",
                        powerTime,
                        P_F.format(targetValvePercent));
            }
    }

    private Integer getCurrentValvePercent() {
        try {
            logger.debug("Проверяем текущий процент открытия клапана");
            float currentVoltageInV =
                    (float) modbusService.readHoldingRegister(dacConfiguration.getAddress(),
                            dacConfiguration.getRegister())
                            / 100;
            logger.debug("Текущее напряжение на ЦАП {}", VD_F.format(currentVoltageInV));
            int currentPercent = getPercentFromVoltageInVWithCorrection(currentVoltageInV);
            logger.debug("Текущий процент открытия клапана по ЦАП {}", P_F.format(currentPercent));
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
        logger.debug("Текущий процент открытия клапана по температуре {}", P_F.format(calculated));
        return calculated;
    }

    private int getPercentFromVoltageInVWithCorrection(float voltage) {
        /* процент считается в интервале напряжений от 2 до 10 В */
        if (voltage < 2) {
            voltage = 2;
            logger.warn("Напряжение на ЦАП клапана теплого пола менее 2В!");
        }
        if (voltage > 10) {
            voltage = 10;
            logger.warn("Напряжение на ЦАП клапана теплого пола более 2В!");
        }
        /* клапан работает в интервале напряжений от 2 до 10 В */
        int correctedPercent = Math.round(((voltage - 2) / 8) * 100);
        if (correctedPercent == 0) {
            return 0;
        }
        /* компенсируем коррекцию */
        return Math.round((correctedPercent - dacConfiguration.getCorrectionConstant()) / dacConfiguration.getCorrectionGradient());
    }

    private float getVoltageInVFromPercentWithCorrection(int percent) {
        /* поскольку клапан открывается неравномерно нужна коррекция */
        /* эта коррекция по линейной функции и весьма приблизительна */
        /* если процент открытия 0 или -1 - корректировать не надо, нужно вернуть 0 */
        int correctedPercent = (percent == 0 || percent == -1)  ? 0 :
                Math.round (dacConfiguration.getCorrectionGradient() * percent + dacConfiguration.getCorrectionConstant());
        logger.debug("Расчетный процент с коррекцией {}", P_F.format(correctedPercent));
        return getVoltageInVFromPercent(correctedPercent);
    }

    private float getVoltageInVFromPercent(int percent) {
        if (percent == -1) {
            percent = 0;
            logger.debug("Калибровка");
        }
        if (percent == 0) {
            logger.debug("Полное закрытие клапана");
        }
        if (percent < 0) {
            percent = 0;
            logger.warn("Задан отрицательный процент открытия клапана теплого пола!");
        }
        if (percent > 100) {
            percent = 100;
            logger.warn("Задан процент открытия клапана теплого пола более 100!");
        }

        /* клапан работает в интервале напряжений от 2 до 10 В */
        return 2f + 8f * percent / 100;
    }

    @Override
    public String getFormattedStatus() {
        Float targetDirectTemperature = calculateTargetDirectTemperature();
        String formattedTargetDirectTemperature;
        if (targetDirectTemperature == null) {
            formattedTargetDirectTemperature=  "ошибка расчета!";
        } else {
            formattedTargetDirectTemperature = TD_F.format(targetDirectTemperature);
        }

        return "текущий процент подмеса в теплые полы - " + getCurrentValvePercent() + "%" + "\n* " +
                "целевая температура подачи в теплые полы " + formattedTargetDirectTemperature;
    }
}

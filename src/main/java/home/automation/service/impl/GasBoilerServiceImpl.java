package home.automation.service.impl;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.BypassRelayStatus;
import home.automation.enums.FloorHeatingStatus;
import home.automation.enums.GasBoilerHeatRequestStatus;
import home.automation.enums.GasBoilerRelayStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.GasBoilerRelaySetFailEvent;
import home.automation.event.info.BypassRelayStatusCalculatedEvent;
import home.automation.event.info.FloorHeatingStatusCalculatedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.BypassRelayService;
import home.automation.service.FloorHeatingService;
import home.automation.service.GasBoilerService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GasBoilerServiceImpl implements GasBoilerService {
    private static final Logger logger = LoggerFactory.getLogger(GasBoilerServiceImpl.class);
    private final GasBoilerConfiguration configuration;
    private final ModbusService modbusService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TemperatureSensorsService temperatureSensorsService;
    private final BypassRelayService bypassRelayService;
    private final FloorHeatingService floorHeatingService;
    private final Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = new HashMap<>();
    private final Map<Instant, Float> gasBoilerDirectWhenWorkTemperatureDailyHistory = new HashMap<>();
    private final Map<Instant, Float> gasBoilerReturnWhenWorkTemperatureDailyHistory = new HashMap<>();
    private GasBoilerStatus status = GasBoilerStatus.INIT;
    private Float lastDirectTemperature = null;
    private Float maxDirectTemperatureForPeriod = null;
    private GasBoilerHeatRequestStatus heatRequestStatus = GasBoilerHeatRequestStatus.INIT;

    public GasBoilerServiceImpl(
        GasBoilerConfiguration configuration,
        ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher,
        TemperatureSensorsService temperatureSensorsService,
        BypassRelayService bypassRelayService,
        FloorHeatingService floorHeatingService,
        MeterRegistry meterRegistry
    ) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.temperatureSensorsService = temperatureSensorsService;
        this.bypassRelayService = bypassRelayService;
        this.floorHeatingService = floorHeatingService;

        Gauge.builder("gas_boiler", this::getNumericStatus)
            .tag("component", "status")
            .tag("system", "home_automation")
            .description("Статус газового котла")
            .register(meterRegistry);

        Gauge.builder("gas_boiler", this::getTemperatureDeltaIfWorks)
            .tag("component", "delta")
            .tag("system", "home_automation")
            .description("Дельта подачи/обратки при работе")
            .register(meterRegistry);

        Gauge.builder("gas_boiler", this::getAverage3HourlyPower)
            .tag("component", "average_3_hour_power")
            .tag("system", "home_automation")
            .description("Средняя мощность за 3 часа")
            .register(meterRegistry);

        Gauge.builder("gas_boiler", this::getGasBoilerRelayNumericStatus)
            .tag("component", "relay_status")
            .tag("system", "home_automation")
            .description("Статус реле газового котла")
            .register(meterRegistry);
    }

    @EventListener
    public void onApplicationEvent(BypassRelayStatusCalculatedEvent event) {
        logger.debug("Получено событие о расчете статуса реле байпаса");
        switch (event.getStatus()) {
            case OPEN -> {
                logger.info("Есть запрос на тепло от радиаторов");
                heatRequestStatus = GasBoilerHeatRequestStatus.NEED_HEAT;
            }
            case CLOSED -> {
                /* проверяем, нужно ли теплым полам тепло, если нет - убираем запрос на тепло */
                if (floorHeatingService.getStatus() == FloorHeatingStatus.NO_NEED_HEAT) {
                    heatRequestStatus = GasBoilerHeatRequestStatus.NO_NEED_HEAT;
                    logger.info("Запроса на тепло нет");
                }
            }
        }
    }

    @EventListener
    public void onApplicationEvent(FloorHeatingStatusCalculatedEvent event) {
        logger.debug("Получено событие о расчете запроса тепла в полы");
        switch (event.getStatus()) {
            case NEED_HEAT -> {
                logger.info("Есть запрос на тепло от теплых полов");
                heatRequestStatus = GasBoilerHeatRequestStatus.NEED_HEAT;
            }
            case NO_NEED_HEAT -> {
                /* проверяем, нужно ли радиаторам тепло, если нет - убираем запрос на тепло */
                if (bypassRelayService.getStatus() == BypassRelayStatus.CLOSED) {
                    logger.info("Запроса на тепло нет");
                    heatRequestStatus = GasBoilerHeatRequestStatus.NO_NEED_HEAT;
                }
            }
        }
    }

    private boolean ifGasBoilerCanBeTurnedOn() {
        /* если котел уже работает, то включать его реле можно */
        if (status == GasBoilerStatus.WORKS) {
            return true;
        }
        /* проверяем можно ли уже включать котел по температуре обратки */
        Float returnTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE);

        return returnTemperature == null || returnTemperature < calculateMinReturnTemperature();
    }

    private float calculateMinReturnTemperature() {
        /* рассчитываем температуру обратки в зависимости от температуры на улице (линейная функция) */
        /* в котле BAXI ПЗА устанавливается кривой, каждая кривая имеет свои границы, эти границы и будут координатами нашей прямой по оси X */
        Float outsideTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);

        if (outsideTemperature == null) {
            float targetReturnTemperature = configuration.getTemperatureReturnMax();
            logger.info(
                "Нет информации о температуре на улице, расчетная температура обратки для включения {} C°",
                targetReturnTemperature
            );
            return targetReturnTemperature;
        }

        if (outsideTemperature <= configuration.getTemperatureWeatherCurveMin()) {
            float targetReturnTemperature = configuration.getTemperatureReturnMax();
            logger.info(
                "Температура на улице ниже минимальной температуры климатической кривой, расчетная температура обратки для включения {} C°",
                targetReturnTemperature
            );
            return targetReturnTemperature;
        }

        if (outsideTemperature >= configuration.getTemperatureWeatherCurveMax()) {
            float targetReturnTemperature = configuration.getTemperatureReturnMin();
            logger.info(
                "Температура на улице выше максимальной температуры климатической кривой, расчетная температура обратки для включения {} C°",
                targetReturnTemperature
            );
            return targetReturnTemperature;
        }

        /* решаем задачу нахождения функции для прямой проходящей через 2 точки, по оси X температура на улице, по оси Y температура обратки */

        /* коэффициент наклона прямой */
        float m = (configuration.getTemperatureReturnMin() - configuration.getTemperatureReturnMax()) / (
            configuration.getTemperatureWeatherCurveMax() - configuration.getTemperatureWeatherCurveMin());

        /* свободный член уравнения*/
        float b = configuration.getTemperatureReturnMax() - m * configuration.getTemperatureWeatherCurveMin();

        float targetReturnTemperature = m * outsideTemperature + b;
        logger.info(
            "Температура на улице в пределах климатической кривой, расчетная температура обратки для включения {} C°",
            targetReturnTemperature
        );
        return targetReturnTemperature;
    }

    private void turnOn() {
        try {
            logger.info("Включаем газовый котел");
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
        }
    }

    private void turnOff() {
        try {
            logger.info("Отключаем газовый котел");
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
        }
    }

    private GasBoilerRelayStatus getGasBoilerRelayStatus() {
        try {
            boolean[] pollResult = modbusService.readAllCoilsFromZero(configuration.getAddress());
            if (pollResult.length < 1) {
                throw new ModbusException("Опрос катушек вернул пустой массив");
            }
            if (pollResult[configuration.getCoil()]) {
                return GasBoilerRelayStatus.NO_NEED_HEAT;
            } else {
                return GasBoilerRelayStatus.NEED_HEAT;
            }

        } catch (ModbusException e) {
            logger.error("Ошибка получения статуса реле газового котла", e);
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
            return GasBoilerRelayStatus.ERROR;
        }
    }

    private int getGasBoilerRelayNumericStatus() {
        return getGasBoilerRelayStatus().getNumericStatus();
    }

    @Scheduled(fixedRateString = "${gasBoiler.relay.updateInterval}")
    private void manageGasBoilerRelay() {
        if (heatRequestStatus == GasBoilerHeatRequestStatus.NEED_HEAT
            && getGasBoilerRelayStatus() != GasBoilerRelayStatus.NEED_HEAT) {
            if (ifGasBoilerCanBeTurnedOn()) {
                turnOn();
            } else {
                logger.info("Газовый котел не может быть включен на отопление по политике тактования");
            }
            return;
        }
        if (heatRequestStatus == GasBoilerHeatRequestStatus.NO_NEED_HEAT
            && getGasBoilerRelayStatus() != GasBoilerRelayStatus.NO_NEED_HEAT) {
            turnOff();
        }
    }

    @Scheduled(fixedRateString = "${gasBoiler.direct.pollInterval}")
    private void calculateStatus() {
        logger.debug("Запущена задача расчета статуса газового котла");

        /* если реле отключено - статус котла считать не имеет смысла */
        if (getGasBoilerRelayStatus() == GasBoilerRelayStatus.NO_NEED_HEAT) {
            setStatus(GasBoilerStatus.IDLE);
            return;
        }

        Float newDirectTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE);

        if (newDirectTemperature == null) {
            logger.warn("Не удалось вычислить статус газового котла");
            setStatus(GasBoilerStatus.ERROR);
            lastDirectTemperature = null;
            maxDirectTemperatureForPeriod = null;
            /* Можно сделать событие о невозможности рассчитать статус газового котла. Но зачем оно? */
            return;
        }

        if (lastDirectTemperature == null) {
            logger.debug("Появилась температура, но статус котла пока неизвестен");
            status = GasBoilerStatus.INIT;
            lastDirectTemperature = newDirectTemperature;
            return;
        }

        /* считаем, что котел работает когда температура подачи растет либо не слишком сильно упала относительно максимума за период работы */
        if (newDirectTemperature > lastDirectTemperature || ((maxDirectTemperatureForPeriod != null
            && newDirectTemperature > maxDirectTemperatureForPeriod - configuration.getTurnOffDirectDelta()))) {
            logger.info("Статус газового котла - работает");
            setStatus(GasBoilerStatus.WORKS);

            if (maxDirectTemperatureForPeriod == null || newDirectTemperature > maxDirectTemperatureForPeriod) {
                maxDirectTemperatureForPeriod = newDirectTemperature;
            }
            putGasBoilerDirectWhenWorkTemperatureToDailyHistory(newDirectTemperature);
            Float newReturnTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE);
            if (newReturnTemperature != null) {
                putGasBoilerReturnWhenWorkTemperatureToDailyHistory(newReturnTemperature);
            }
        } else {
            logger.info("Статус газового котла - не работает");
            setStatus(GasBoilerStatus.IDLE);
            maxDirectTemperatureForPeriod = null;
        }
        lastDirectTemperature = newDirectTemperature;
    }

    private void putGasBoilerStatusToDailyHistory(GasBoilerStatus calculatedStatus) {
        gasBoilerStatusDailyHistory.put(Instant.now(), calculatedStatus);
        gasBoilerStatusDailyHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private void putGasBoilerDirectWhenWorkTemperatureToDailyHistory(Float temperature) {
        if (temperature != null) {
            gasBoilerDirectWhenWorkTemperatureDailyHistory.put(Instant.now(), temperature);
        }
        gasBoilerDirectWhenWorkTemperatureDailyHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private void putGasBoilerReturnWhenWorkTemperatureToDailyHistory(Float temperature) {
        if (temperature != null) {
            gasBoilerReturnWhenWorkTemperatureDailyHistory.put(Instant.now(), temperature);
        }
        gasBoilerReturnWhenWorkTemperatureDailyHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    @Override
    public GasBoilerStatus getStatus() {
        return status;
    }

    private void setStatus(GasBoilerStatus newStatus) {
        putGasBoilerStatusToDailyHistory(newStatus);

        if (status != newStatus) {
            if (status == GasBoilerStatus.IDLE && newStatus == GasBoilerStatus.WORKS) {
                logger.info("Газовый котел только что включился");
            }
            if (status == GasBoilerStatus.WORKS && newStatus == GasBoilerStatus.IDLE) {
                logger.info("Газовый котел только что отключился, блокируем реле");
                turnOff();
            }

            status = newStatus;
        }
    }

    private int getNumericStatus() {
        return status.getNumericStatus();
    }

    private Float getTemperatureDeltaIfWorks() {
        Float gasBoilerDirectTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE);
        Float gasBoilerReturnTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE);

        if (getStatus() != GasBoilerStatus.WORKS || gasBoilerDirectTemperature == null
            || gasBoilerReturnTemperature == null) {
            return null;
        } else {
            return gasBoilerDirectTemperature - gasBoilerReturnTemperature;
        }
    }

    private Float getAverage3HourlyPower() {
        if (!(gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.IDLE)
            || gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.WORKS))
            || gasBoilerDirectWhenWorkTemperatureDailyHistory.isEmpty()
            || gasBoilerReturnWhenWorkTemperatureDailyHistory.isEmpty()) {
            return null;
        }

        /* история работы котла собирается за сутки, а нам нужно за три часа, поэтому отрезаем лишнее */
        Map<Instant, GasBoilerStatus> gasBoilerStatusHourlyHistory = gasBoilerStatusDailyHistory.entrySet().stream()
            .filter(status -> status.getKey().isAfter(Instant.now().minus(3, ChronoUnit.HOURS)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Pair<List<Float>, List<Float>> intervals = calculateWorkIdleIntervals(gasBoilerStatusHourlyHistory);
        float workPercent = calculateWorkPercent(intervals);

        /* аналогично для расчета среднечасовой дельты */
        Map<Instant, Float> gasBoilerDirectWhenWorkTemperatureHourlyHistory =
            gasBoilerDirectWhenWorkTemperatureDailyHistory.entrySet().stream()
                .filter(temperature -> temperature.getKey().isAfter(Instant.now().minus(3, ChronoUnit.HOURS)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Instant, Float> gasBoilerReturnWhenWorkTemperatureHourlyHistory =
            gasBoilerReturnWhenWorkTemperatureDailyHistory.entrySet().stream()
                .filter(temperature -> temperature.getKey().isAfter(Instant.now().minus(3, ChronoUnit.HOURS)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        float delta = calculateAverageTemperatureDeltaWhenWork(gasBoilerDirectWhenWorkTemperatureHourlyHistory,
            gasBoilerReturnWhenWorkTemperatureHourlyHistory
        );

        return calculateAveragePowerInkW(delta, workPercent);
    }

    @Override
    public String getFormattedStatus() {
        return status.getTemplate();
    }

    @Override
    public String getFormattedStatusForLastDay() {
        if (!(gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.IDLE)
            || gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.WORKS))
            || gasBoilerDirectWhenWorkTemperatureDailyHistory.isEmpty()
            || gasBoilerReturnWhenWorkTemperatureDailyHistory.isEmpty()) {
            return "сведений о работе газового котла пока не достаточно";
        }
        Pair<List<Float>, List<Float>> intervals = calculateWorkIdleIntervals(gasBoilerStatusDailyHistory);

        Pair<Float, Float> averageTimes = calculateAverageTimes(intervals);

        float averageWorkTime = averageTimes.getLeft();
        float averageIdleTime = averageTimes.getRight();
        float workPercent = calculateWorkPercent(intervals);
        float averageTemperatureDeltaWhenWorks = calculateAverageTemperatureDeltaWhenWork(
            gasBoilerDirectWhenWorkTemperatureDailyHistory,
            gasBoilerReturnWhenWorkTemperatureDailyHistory
        );
        float averagePowerInkW = calculateAveragePowerInkW(averageTemperatureDeltaWhenWorks, workPercent);

        DecimalFormat df0 = new DecimalFormat("#");
        DecimalFormat df1 = new DecimalFormat("#.#");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        Instant oldestTimestampIntDataset = Collections.min(gasBoilerStatusDailyHistory.keySet());
        String intro = oldestTimestampIntDataset.isBefore(Instant.now().minus(23, ChronoUnit.HOURS))
            ? "за последние сутки котел работал на отопление "
            : "начиная с " + dtf.format(LocalDateTime.ofInstant(oldestTimestampIntDataset, ZoneId.systemDefault()))
                + " котел работал на отопление ";

        return intro + df0.format(workPercent) + "% времени\n* среднее время работы/простоя " + df1.format(
            averageWorkTime) + "/" + df1.format(averageIdleTime) + " мин\n"
            + "* средняя дельта подачи/обратки при работе " + df1.format(averageTemperatureDeltaWhenWorks) + " C° \n"
            + "* среднесуточная мощность " + df1.format(averagePowerInkW) + " кВт";
    }

    private Pair<List<Float>, List<Float>> calculateWorkIdleIntervals(Map<Instant, GasBoilerStatus> gasBoilerStatusHistory) {
        /* создаем shallow копию датасета и очищаем его от ненужных записей */
        Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistoryCleared = new HashMap<>(gasBoilerStatusHistory);
        gasBoilerStatusDailyHistoryCleared.entrySet()
            .removeIf(entry -> (entry.getValue() != GasBoilerStatus.WORKS && entry.getValue() != GasBoilerStatus.IDLE));

        /* для работы нужен отсортированный список времен */
        List<Instant> timestamps = new ArrayList<>(gasBoilerStatusDailyHistoryCleared.keySet());
        Collections.sort(timestamps);

        List<Float> workIntervals = new ArrayList<>();
        List<Float> idleIntervals = new ArrayList<>();

        /* запоминаем начало интервала - первый элемент очищенного датасета */
        Instant intervalBeginTimestamp = timestamps.get(0);
        GasBoilerStatus intervalBeginStatus = gasBoilerStatusDailyHistoryCleared.get(intervalBeginTimestamp);
        /* и убираем его, чтобы начать со второго */
        gasBoilerStatusDailyHistoryCleared.remove(intervalBeginTimestamp);
        timestamps.remove(intervalBeginTimestamp);

        /* добавляем в списке интервалы */
        for (Instant timestamp : timestamps) {
            if (gasBoilerStatusDailyHistoryCleared.get(timestamp) != intervalBeginStatus) {
                float durationInMinutes =
                    (float) (Duration.between(intervalBeginTimestamp, timestamp).toSeconds() / 60);
                if (intervalBeginStatus == GasBoilerStatus.IDLE) {
                    idleIntervals.add(durationInMinutes);
                }
                if (intervalBeginStatus == GasBoilerStatus.WORKS) {
                    workIntervals.add(durationInMinutes);
                }
                intervalBeginTimestamp = timestamp;
                intervalBeginStatus = gasBoilerStatusDailyHistoryCleared.get(timestamp);
            }
        }
        /* если последний интервал не закрыт - закрываем вручную */
        if (intervalBeginTimestamp != null) {
            float durationInMinutes =
                (float) (Duration.between(intervalBeginTimestamp, Instant.now()).toSeconds() / 60);
            if (intervalBeginStatus == GasBoilerStatus.IDLE) {
                idleIntervals.add(durationInMinutes);
            }
            if (intervalBeginStatus == GasBoilerStatus.WORKS) {
                workIntervals.add(durationInMinutes);
            }
        }
        return Pair.of(workIntervals, idleIntervals);
    }

    private float calculateWorkPercent(Pair<List<Float>, List<Float>> intervals) {
        float countWorks = (float) intervals.getLeft().stream().mapToDouble(t -> t).sum();
        float countIdle = (float) intervals.getRight().stream().mapToDouble(t -> t).sum();
        /* подпираем для ситуаций когда котел только что был опрошен в первый раз и не прошло еще минуты */
        float result = countWorks / (countWorks + countIdle) * 100;
        if (!Float.isNaN(result)) {
            return result;
        } else {
            return 0;
        }
    }

    private Pair<Float, Float> calculateAverageTimes(Pair<List<Float>, List<Float>> intervals) {
        float averageWorkTime = (float) intervals.getLeft().stream().mapToDouble(t -> t).average().orElse(0f);
        float averageIdleTime = (float) intervals.getRight().stream().mapToDouble(t -> t).average().orElse(0f);
        return Pair.of(averageWorkTime, averageIdleTime);
    }

    private float calculateAverageTemperatureDeltaWhenWork(
        Map<Instant, Float> gasBoilerDirectWhenWorkTemperatureHistory,
        Map<Instant, Float> gasBoilerReturnWhenWorkTemperatureHistory
    ) {
        float averageDirect =
            (float) (gasBoilerDirectWhenWorkTemperatureHistory.values().stream().mapToDouble(t -> t).average()
                .getAsDouble());
        float averageReturn =
            (float) (gasBoilerReturnWhenWorkTemperatureHistory.values().stream().mapToDouble(t -> t).average()
                .getAsDouble());
        return averageDirect - averageReturn;
    }

    private float calculateAveragePowerInkW(float averageDelta, float workPercent) {
        if (workPercent == 0) {
            return 0f;
        }
        /* формула расчета мощности Q = m * с * ΔT, где m - масса теплоносителя, а c его теплоемкость.
        Теплоемкость воды 4200 Вт/°C), масса теплоносителя считается в кубометрах в час, поэтому формула выглядит так:
        Q = (1000/3600 * m м3/ч) * (4200 Вт/°C) * ΔT °C = 1.163 кВт/°C * m м3/ч * ΔT °C */
        float powerWhenWorks = (float) (1.163 * configuration.getWaterFlow() * averageDelta);
        return powerWhenWorks * workPercent / 100;
    }
}

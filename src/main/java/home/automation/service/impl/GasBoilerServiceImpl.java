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

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.BypassRelayStatus;
import home.automation.enums.FloorHeatingStatus;
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

    private final TemperatureSensor waterDirectGasBoilerTemperature =
        TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE;

    private final GasBoilerConfiguration configuration;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final TemperatureSensorsService temperatureSensorsService;

    private final BypassRelayService bypassRelayService;

    private final FloorHeatingService floorHeatingService;

    private final Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = new HashMap<>();

    private final Map<Instant, Float> gasBoilerReturnTemperatureHistory = new HashMap<>();

    private GasBoilerStatus calculatedStatus = GasBoilerStatus.INIT;

    private GasBoilerRelayStatus relayStatus = GasBoilerRelayStatus.INIT;

    private Float lastDirectTemperature;

    public GasBoilerServiceImpl(
        GasBoilerConfiguration configuration,
        ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher,
        TemperatureSensorsService temperatureSensorsService,
        BypassRelayService bypassRelayService,
        FloorHeatingService floorHeatingService
    ) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.temperatureSensorsService = temperatureSensorsService;
        this.bypassRelayService = bypassRelayService;
        this.floorHeatingService = floorHeatingService;
    }

    @EventListener
    public void onApplicationEvent(BypassRelayStatusCalculatedEvent event) {
        logger.debug("Получено событие о расчете статуса реле байпаса");
        switch (event.getStatus()) {
            case OPEN -> turnOn();
            case CLOSED -> {
                // проверяем, нужно ли теплым полам тепло, если нет - гасим котел
                if (floorHeatingService.getStatus() == FloorHeatingStatus.NO_NEED_HEAT) {
                    turnOff();
                }
            }
        }
    }

    @EventListener
    public void onApplicationEvent(FloorHeatingStatusCalculatedEvent event) {
        logger.debug("Получено событие о расчете запроса тепла в полы");
        switch (event.getStatus()) {
            case NEED_HEAT -> turnOn();
            case NO_NEED_HEAT -> {
                // проверяем, нужно ли радиаторам тепло, если нет - гасим котел
                if (bypassRelayService.getStatus() == BypassRelayStatus.CLOSED) {
                    turnOff();
                }
            }
        }
    }

    private void turnOn() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
            relayStatus = GasBoilerRelayStatus.NEED_HEAT;
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
            relayStatus = GasBoilerRelayStatus.ERROR;
        }
    }

    private void turnOff() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
            relayStatus = GasBoilerRelayStatus.NO_NEED_HEAT;
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
            relayStatus = GasBoilerRelayStatus.ERROR;
        }
    }

    @Scheduled(fixedRateString = "${gasBoiler.direct.pollInterval}")
    private void calculateStatus() {
        logger.debug("Запущена задача расчета статуса газового котла");
        if (GasBoilerRelayStatus.NO_NEED_HEAT == relayStatus) {
            logger.debug("Реле котла не замкнуто, значит газовый котел не работает");
            calculatedStatus = GasBoilerStatus.IDLE;
            return;
        }

        Float newDirectTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(waterDirectGasBoilerTemperature);

        if (newDirectTemperature == null) {
            logger.warn("Не удалось вычислить статус газового котла");
            calculatedStatus = GasBoilerStatus.ERROR;
            lastDirectTemperature = null;
            /* Можно сделать событие о невозможности рассчитать статус газового котла. Но зачем оно? */
            return;
        }

        if (lastDirectTemperature == null) {
            logger.debug("Появилась температура, но статус котла пока неизвестен");
            calculatedStatus = GasBoilerStatus.ERROR;
            lastDirectTemperature = newDirectTemperature;
            return;
        }

        GasBoilerStatus newCalculatedStatus;

        if (newDirectTemperature >= lastDirectTemperature) {
            logger.debug("Котел работает");
            newCalculatedStatus = GasBoilerStatus.WORKS;
        } else {
            logger.debug("Котел не работает");
            newCalculatedStatus = GasBoilerStatus.IDLE;
        }

        putGasBoilerStatusToDailyHistory(calculatedStatus);
        if (calculatedStatus == GasBoilerStatus.IDLE && newCalculatedStatus == GasBoilerStatus.WORKS) {
            putGasBoilerReturnTemperatureToDailyHistory();
        }

        lastDirectTemperature = newDirectTemperature;
        calculatedStatus = newCalculatedStatus;
    }

    private void putGasBoilerStatusToDailyHistory(GasBoilerStatus calculatedStatus) {
        gasBoilerStatusDailyHistory.put(Instant.now(), calculatedStatus);
        gasBoilerStatusDailyHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private void putGasBoilerReturnTemperatureToDailyHistory() {
        gasBoilerReturnTemperatureHistory.put(
            Instant.now(),
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE)
        );
        gasBoilerReturnTemperatureHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    @Override
    public GasBoilerStatus getStatus() {
        return calculatedStatus;
    }

    @Override
    public String getFormattedStatus() {
        return calculatedStatus.getTemplate();
    }

    @Override
    public String getFormattedStatusForLastDay() {
        if (gasBoilerStatusDailyHistory.isEmpty() || !gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.IDLE)
            || !gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.WORKS)
            || gasBoilerReturnTemperatureHistory.isEmpty()) {
            return "сведений о работе газового котла пока не достаточно";
        }
        Pair<List<Float>, List<Float>> intervals = calculateWorkIdleIntervals();

        Pair<Float, Float> averageTimes = calculateAverageTimes(intervals);
        float averageWorkTime = averageTimes.getLeft();
        float averageIdleTime = averageTimes.getRight();

        DecimalFormat df0 = new DecimalFormat("#");
        DecimalFormat df1 = new DecimalFormat("#.#");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        Instant oldestTimestampIntDataset = Collections.min(gasBoilerStatusDailyHistory.keySet());
        String intro = oldestTimestampIntDataset.isBefore(Instant.now().minus(23, ChronoUnit.HOURS))
            ? "за последние сутки газовый котел работал на отопление "
            : "начиная с " + dtf.format(LocalDateTime.ofInstant(oldestTimestampIntDataset, ZoneId.systemDefault()))
                + " газовый котел работал на отопление ";

        return intro + df0.format(calculateWorkPercent(intervals)) + "% времени\n* среднее время работы/простоя "
            + df1.format(averageWorkTime) + "/" + df1.format(averageIdleTime)
            + " мин\n* средняя температура обратки при запуске "
            + df1.format(calculateAverageGasBoilerReturnTemperature()) + " C°";
    }

    private Pair<List<Float>, List<Float>> calculateWorkIdleIntervals() {
        /* создаем shallow копию датасета и очищаем его от ненужных записей */
        Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistoryCleared = new HashMap<>(gasBoilerStatusDailyHistory);
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
        return countWorks / (countWorks + countIdle) * 100;
    }

    private Pair<Float, Float> calculateAverageTimes(Pair<List<Float>, List<Float>> intervals) {
        float averageWorkTime = (float) intervals.getLeft().stream().mapToDouble(t -> t).average().orElse(0f);
        float averageIdleTime = (float) intervals.getRight().stream().mapToDouble(t -> t).average().orElse(0f);
        return Pair.of(averageWorkTime, averageIdleTime);
    }

    private float calculateAverageGasBoilerReturnTemperature() {
        return (float) gasBoilerReturnTemperatureHistory.values().stream().mapToDouble(t -> t).average().orElse(0f);
    }
}

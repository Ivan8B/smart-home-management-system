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
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.service.HistoryService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

@Service
public class HistoryServiceImpl implements HistoryService {
    private final GasBoilerConfiguration gasBoilerConfiguration;
    private final Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = new HashMap<>();
    private final Map<Instant, Float> gasBoilerDirectTemperatureDailyHistory = new HashMap<>();
    private final Map<Instant, Float> gasBoilerReturnTemperatureDailyHistory = new HashMap<>();

    public HistoryServiceImpl(MeterRegistry meterRegistry, GasBoilerConfiguration gasBoilerConfiguration) {
        this.gasBoilerConfiguration = gasBoilerConfiguration;
        Gauge.builder("gas_boiler", this::getGasBoilerAverage3HourlyPower)
            .tag("component", "average_3_hour_power")
            .tag("system", "home_automation")
            .description("Средняя мощность за 3 часа")
            .register(meterRegistry);
    }

    @Override
    public void putGasBoilerStatusToDailyHistory(GasBoilerStatus status, Instant ts) {
        gasBoilerStatusDailyHistory.put(ts, status);
        gasBoilerStatusDailyHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    @Override
    public void putTemperatureToDailyHistory(TemperatureSensor sensor, Float temperature, Instant ts) {
        if (temperature != null) {
            switch (sensor) {
                case WATER_DIRECT_GAS_BOILER_TEMPERATURE -> gasBoilerDirectTemperatureDailyHistory.put(ts, temperature);
                case WATER_RETURN_GAS_BOILER_TEMPERATURE -> gasBoilerReturnTemperatureDailyHistory.put(ts, temperature);
            }
        }
        gasBoilerDirectTemperatureDailyHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
        gasBoilerReturnTemperatureDailyHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private Float getGasBoilerAverage3HourlyPower() {
        if (!(gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.IDLE)
            || gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.WORKS))
            || gasBoilerDirectTemperatureDailyHistory.isEmpty()
            || gasBoilerReturnTemperatureDailyHistory.isEmpty()) {
            return null;
        }

        /* история работы котла собирается за сутки, а нам нужно за три часа, поэтому отрезаем лишнее */
        Map<Instant, GasBoilerStatus> gasBoilerStatus3HourHistory = gasBoilerStatusDailyHistory.entrySet().stream()
            .filter(status -> status.getKey().isAfter(Instant.now().minus(3, ChronoUnit.HOURS)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Pair<List<Float>, List<Float>> intervals = calculateWorkIdleIntervals(gasBoilerStatus3HourHistory);
        float work3HourPercent = calculateWorkPercent(intervals);

        /* аналогично для расчета среднечасовой дельты, еще и фильтруем только те записи, когда котел работал */
        Map<Instant, Float> gasBoilerDirectWhenWorkTemperature3HourHistory =
            gasBoilerDirectTemperatureDailyHistory.entrySet().stream()
                .filter(temperature -> temperature.getKey().isAfter(Instant.now().minus(3, ChronoUnit.HOURS)))
                .filter(temperature -> gasBoilerStatus3HourHistory.containsKey(temperature.getKey())
                    && gasBoilerStatus3HourHistory.get(temperature.getKey()) == GasBoilerStatus.WORKS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Instant, Float> gasBoilerReturnWhenWorkTemperature3HourHistory =
            gasBoilerReturnTemperatureDailyHistory.entrySet().stream()
                .filter(temperature -> temperature.getKey().isAfter(Instant.now().minus(3, ChronoUnit.HOURS)))
                .filter(temperature -> gasBoilerStatus3HourHistory.containsKey(temperature.getKey())
                    && gasBoilerStatus3HourHistory.get(temperature.getKey()) == GasBoilerStatus.WORKS)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        /* еще раз проверяем, что история не пустая */
        if (gasBoilerReturnWhenWorkTemperature3HourHistory.isEmpty() || gasBoilerReturnTemperatureDailyHistory.isEmpty()) {
            return null;
        }
        /* рассчитываем среднюю дельту за 3 часа за время работы котла */
        float delta = calculateAverageTemperatureDeltaWhenWork(
            gasBoilerDirectWhenWorkTemperature3HourHistory,
            gasBoilerReturnWhenWorkTemperature3HourHistory
        );

        return calculateAverageGasBoilerPowerInkW(delta, work3HourPercent);
    }

    @Override
    public String getGasBoilerFormattedStatusForLastDay() {
        if (!(gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.IDLE)
            || gasBoilerStatusDailyHistory.containsValue(GasBoilerStatus.WORKS))
            || gasBoilerDirectTemperatureDailyHistory.isEmpty()
            || gasBoilerReturnTemperatureDailyHistory.isEmpty()) {
            return "сведений о работе газового котла пока не достаточно";
        }

        Pair<List<Float>, List<Float>> intervals = calculateWorkIdleIntervals(gasBoilerStatusDailyHistory);
        float workPercent = calculateWorkPercent(intervals);
        float averageTurnOnPerHour = calculateAverageTurnOnPerHour(intervals);

        DecimalFormat df0 = new DecimalFormat("#");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        Instant oldestTimestampIntDataset = Collections.min(gasBoilerStatusDailyHistory.keySet());
        String intro = oldestTimestampIntDataset.isBefore(Instant.now().minus(23, ChronoUnit.HOURS))
            ? "за последние сутки котел работал на отопление "
            : "начиная с " + dtf.format(LocalDateTime.ofInstant(oldestTimestampIntDataset, ZoneId.systemDefault()))
                + " котел работал на отопление ";

        return intro + df0.format(workPercent) + "% времени\n* среднее количество розжигов в час " + averageTurnOnPerHour;
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

    private float calculateAverageTurnOnPerHour(Pair<List<Float>, List<Float>> intervals) {
        int countWorks = intervals.getLeft().size();
        Instant oldestTimestampIntDataset = Collections.min(gasBoilerStatusDailyHistory.keySet());
        Duration interval = Duration.between(oldestTimestampIntDataset, Instant.now());
        long countHours = interval.toHours();
        /* если прошло не больше часа - возвращаем чисто включений */
        if (countHours == 0) {
            return countWorks;
        }
        return ((float) countWorks) / countHours;
    }

    private float calculateAverageTemperatureDeltaWhenWork(
        Map<Instant, Float> gasBoilerDirectWhenWorkTemperatureHistory,
        Map<Instant, Float> gasBoilerReturnWhenWorkTemperatureHistory
    ) {
        float averageDirect =
            (float) (gasBoilerDirectWhenWorkTemperatureHistory.values().stream().mapToDouble(t -> t).average()
                .orElse(0f));
        float averageReturn =
            (float) (gasBoilerReturnWhenWorkTemperatureHistory.values().stream().mapToDouble(t -> t).average()
                .orElse(0f));
        return averageDirect - averageReturn;
    }

    private float calculateAverageGasBoilerPowerInkW(float averageDelta, float workPercent) {
        if (workPercent == 0) {
            return 0f;
        }
    /* Формула расчета мощности Q = m * с * ΔT, где m - масса теплоносителя, а c его теплоемкость.
    Теплоемкость воды 4200 Вт/°C), масса теплоносителя считается в кубометрах в час, поэтому формула выглядит так:
    Q = (1000/3600 * m м3/ч) * (4200 Вт/°C) * ΔT °C = 1.163 кВт/°C * m м3/ч * ΔT °C */
        float powerWhenWorks = (float) (1.163 * gasBoilerConfiguration.getWaterFlow() * averageDelta);
        return powerWhenWorks * workPercent / 100;
    }
}

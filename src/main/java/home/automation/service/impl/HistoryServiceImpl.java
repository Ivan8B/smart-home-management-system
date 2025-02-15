package home.automation.service.impl;

import home.automation.configuration.FloorHeatingConfiguration;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.service.HistoryService;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

@Service
public class HistoryServiceImpl implements HistoryService {
    private final FloorHeatingConfiguration floorHeatingConfiguration;
    private final Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = new HashMap<>();
    private final Map<Instant, Float> gasBoilerDirectTemperatureDailyHistory = new HashMap<>();
    private final Map<Instant, Float> gasBoilerReturnTemperatureDailyHistory = new HashMap<>();
    private final Map<Instant, Integer> calculatedValvePercentLastNValues = new HashMap<>();

    public HistoryServiceImpl(FloorHeatingConfiguration floorHeatingConfiguration) {
        this.floorHeatingConfiguration = floorHeatingConfiguration;
    }

    @Override
    public synchronized void putGasBoilerStatusToDailyHistory(GasBoilerStatus status, Instant ts) {
        /* не добавляем если статус такой же, как и предыдущий, экономим память */
        if (status != getLastGasBoilerStatus()) {
            gasBoilerStatusDailyHistory.put(ts, status);
        }
        gasBoilerStatusDailyHistory.entrySet()
                .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private GasBoilerStatus getLastGasBoilerStatus() {
        Optional<Instant> maxKey = gasBoilerStatusDailyHistory.keySet().stream().max(Comparator.naturalOrder());
        return maxKey.map(gasBoilerStatusDailyHistory::get).orElse(null);
    }

    @Override
    public synchronized void putTemperatureToDailyHistory(TemperatureSensor sensor, Float temperature, Instant ts) {
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
        DecimalFormat df1 = new DecimalFormat("#.#");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

        Instant oldestTimestampIntDataset = Collections.min(gasBoilerStatusDailyHistory.keySet());
        String intro = oldestTimestampIntDataset.isBefore(Instant.now().minus(23, ChronoUnit.HOURS))
                ? "за последние сутки котел работал на отопление "
                : "начиная с " + dtf.format(LocalDateTime.ofInstant(oldestTimestampIntDataset, ZoneId.systemDefault()))
                + " котел работал на отопление ";

        return intro +
                df0.format(workPercent) +
                "% времени\n* среднее количество розжигов в час " +
                df1.format(averageTurnOnPerHour);
    }

    @Override
    public Duration getGasBoilerCurrentStatusDuration() {
        Instant lastKey = gasBoilerStatusDailyHistory.keySet().stream().max(Comparator.naturalOrder()).orElse(null);
        if (lastKey == null) {
            return null;
        }
        return Duration.between(lastKey, Instant.now());
    }

    @Override
    public void putCalculatedTargetValvePercent(Integer calculatedTargetValvePercent, Instant ts) {
        if (calculatedTargetValvePercent != null) {
            calculatedValvePercentLastNValues.put(ts, calculatedTargetValvePercent);
        }
        List<Instant> listOfTop10NewestKeys =
                calculatedValvePercentLastNValues.keySet().stream().sorted(Comparator.reverseOrder())
                        .limit(floorHeatingConfiguration.getValuesCountForAverage()).toList();
        calculatedValvePercentLastNValues.entrySet().removeIf(entry -> !listOfTop10NewestKeys.contains(entry.getKey()));
    }

    @Override
    public Integer getAverageCalculatedTargetValvePercentForLastNValues() {
        if (calculatedValvePercentLastNValues.size() < floorHeatingConfiguration.getValuesCountForAverage()) {
            return null;
        }
        OptionalDouble averageOptional =
                calculatedValvePercentLastNValues.values().stream().mapToDouble(t -> t).average();
        Float average = averageOptional.isPresent() ? (float) averageOptional.getAsDouble() : null;
        return average != null ? Math.round(average) : null;
    }

    @Override
    public Integer getLastCalculatedTargetValvePercent() {
        Optional<Instant> lastKey = calculatedValvePercentLastNValues.keySet().stream().max(Comparator.naturalOrder());
        return lastKey.map(calculatedValvePercentLastNValues::get).orElse(null);
    }

    private Pair<List<Float>, List<Float>> calculateWorkIdleIntervals(Map<Instant, GasBoilerStatus> gasBoilerStatusHistory) {
        /* создаем shallow копию датасета и очищаем его от ненужных записей */
        Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistoryCleared = new HashMap<>(gasBoilerStatusHistory);
        gasBoilerStatusDailyHistoryCleared.entrySet()
                .removeIf(entry -> (entry.getValue() != GasBoilerStatus.WORKS &&
                        entry.getValue() != GasBoilerStatus.IDLE));

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
        }
        else {
            return 0;
        }
    }

    private float calculateAverageTurnOnPerHour(Pair<List<Float>, List<Float>> intervals) {
        int countWorks = intervals.getLeft().size();
        Instant oldestTimestampIntDataset = Collections.min(gasBoilerStatusDailyHistory.keySet());
        Duration interval = Duration.between(oldestTimestampIntDataset, Instant.now());
        double countHours = interval.toMinutes() / 60.0;
        /* если прошло не больше часа - возвращаем чисто включений */
        if (countHours < 1) {
            return countWorks;
        }
        return (float) (countWorks / countHours);
    }
}

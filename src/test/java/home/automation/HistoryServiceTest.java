package home.automation;

import home.automation.configuration.FloorHeatingConfiguration;
import home.automation.enums.GasBoilerStatus;
import home.automation.service.HistoryService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class HistoryServiceTest extends AbstractTest {
    @Autowired
    FloorHeatingConfiguration floorHeatingConfiguration;

    @Autowired
    HistoryService historyService;

    private Map<Instant, GasBoilerStatus> getGasBoilerStatusDailyHistory() {
        try {
            Field field = historyService.getClass().getDeclaredField("gasBoilerStatusDailyHistory");
            field.setAccessible(true);
            return (Map<Instant, GasBoilerStatus>) field.get(historyService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось обратиться к датасету статусов котла", e);
        }
    }

    private void invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus status, Instant ts) {
        try {
            Method method = historyService.getClass().getDeclaredMethod("putGasBoilerStatusToDailyHistory",
                    GasBoilerStatus.class, Instant.class);
            method.setAccessible(true);
            method.invoke(historyService, status, ts);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось добавить статус котла в датасет", e);
        }
    }
    
    private Pair<List<Float>, List<Float>> invokeCalculateWorkIdleIntervalsMethod(Map<Instant, GasBoilerStatus> gasBoilerStatusHistory) {
        try {
            Method method = historyService.getClass().getDeclaredMethod("calculateWorkIdleIntervals", Map.class);
            method.setAccessible(true);
            return (Pair<List<Float>, List<Float>>) method.invoke(historyService, gasBoilerStatusHistory);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета интервалов", e);
        }
    }

    private float invokeCalculateAverageTurnOnPerHourMethod(Pair<List<Float>, List<Float>> intervals) {
        try {
            Method method = historyService.getClass().getDeclaredMethod("calculateAverageTurnOnPerHour", Pair.class);
            method.setAccessible(true);
            return (float) method.invoke(historyService, intervals);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета количества включений в час", e);
        }
    }

    @Test
    @DisplayName("Проверка метода рассчитывающего количество включений котла в час")
    void checkCalculateAverageTurnOnPerHour() {
        /* с момента включения прошло меньше часа */
        Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = getGasBoilerStatusDailyHistory();
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.INIT, Instant.now().minus(10, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(9, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.WORKS, Instant.now().minus(8, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(7, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.ERROR, Instant.now().minus(6, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(5, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.WORKS, Instant.now().minus(4, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(3, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.WORKS, Instant.now().minus(2, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(1, ChronoUnit.MINUTES));

        assertEquals(3f,
                invokeCalculateAverageTurnOnPerHourMethod(invokeCalculateWorkIdleIntervalsMethod(
                        getGasBoilerStatusDailyHistory())));


        /* с момента включения прошло больше часа */
        gasBoilerStatusDailyHistory.clear();
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.INIT, Instant.now().minus(10, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(9, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.WORKS, Instant.now().minus(8, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(7, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.ERROR, Instant.now().minus(6, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(5, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.WORKS, Instant.now().minus(4, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(3, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.WORKS, Instant.now().minus(2, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(1, ChronoUnit.HOURS));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.WORKS, Instant.now().minus(30, ChronoUnit.MINUTES));

        assertEquals(0.4f,
                invokeCalculateAverageTurnOnPerHourMethod(invokeCalculateWorkIdleIntervalsMethod(
                        getGasBoilerStatusDailyHistory())));

    }

    private Map<Instant, Integer> getCalculatedValvePercentLastNValues() {
        try {
            Field field = historyService.getClass().getDeclaredField("calculatedValvePercentLastNValues");
            field.setAccessible(true);
            return (Map<Instant, Integer>) field.get(historyService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось обратиться к датасету рассчитанных процентов открытия клапана", e);
        }
    }

    private void invokePutCalculatedTargetValvePercentMethod(Integer calculatedTargetValvePercent, Instant ts) {
        try {
            Method method = historyService.getClass().getDeclaredMethod("putCalculatedTargetValvePercent",
                    Integer.class, Instant.class);
            method.setAccessible(true);
            method.invoke(historyService, calculatedTargetValvePercent, ts);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод добавления процентов открытия клапана в историю", e);
        }
    }

    @Test
    @DisplayName("Проверка очистки старых данных из датасета рассчитанных процентов открытия клапана")
    void checkCleanOldValuesFromCalculatedValvePercentLast25Values() {
        Instant now = Instant.now();
        for (int i = 0; i < floorHeatingConfiguration.getValuesCountForAverage(); i++) {
            invokePutCalculatedTargetValvePercentMethod(50, now.plus(i, ChronoUnit.MINUTES));
        }
        assertEquals(floorHeatingConfiguration.getValuesCountForAverage(), getCalculatedValvePercentLastNValues().size());
        assertTrue(getCalculatedValvePercentLastNValues().containsKey(now));

        invokePutCalculatedTargetValvePercentMethod(50,
                now.plus(floorHeatingConfiguration.getValuesCountForAverage(), ChronoUnit.MINUTES));
        assertEquals(floorHeatingConfiguration.getValuesCountForAverage(), getCalculatedValvePercentLastNValues().size());
        assertFalse(getCalculatedValvePercentLastNValues().containsKey(now));
    }

    @Test
    @DisplayName("Проверка метода получения времени нахождения газового котла в текущем статусе ")
    void checkGasBoilerCurrentStatusDuration() {
        long deltaSeconds;
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.INIT, Instant.now().minus(10, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(9, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(8, ChronoUnit.MINUTES));
        invokePutGasBoilerStatusToDailyHistory(GasBoilerStatus.IDLE, Instant.now().minus(7, ChronoUnit.MINUTES));
        deltaSeconds = Duration.of(9, ChronoUnit.MINUTES).minus(historyService.getGasBoilerCurrentStatusDuration()).toSeconds();
        assertTrue(Math.abs(deltaSeconds) < 5);
    }

}

package home.automation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.GasBoilerHeatRequestStatus;
import home.automation.enums.GasBoilerRelayStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.exception.ModbusException;
import home.automation.service.FloorHeatingService;
import home.automation.service.GasBoilerService;
import home.automation.service.TemperatureSensorsService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class GasBoilerServiceTest extends AbstractTest {
    @Autowired
    GasBoilerService gasBoilerService;

    @Autowired
    GasBoilerConfiguration configuration;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @MockBean
    TemperatureSensorsService temperatureSensorsService;

    @MockBean
    FloorHeatingService floorHeatingService;

    private void invokeCalculateStatusMethod(GasBoilerRelayStatus status) {
        try {
            if (status == GasBoilerRelayStatus.NO_NEED_HEAT) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                    .thenReturn(new boolean[]{true, false});
            }
            if (status == GasBoilerRelayStatus.NEED_HEAT) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                    .thenReturn(new boolean[]{false, false});
            }
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateStatus");
            method.setAccessible(true);
            method.invoke(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета статуса газового котла", e);
        }
    }

    @Test
    @DisplayName("Проверка правильного расчета статуса котла при запуске (зима) если котел при запуске системы работает")
    void checkStatusCalculationOnTemperatureOnStartWinterWorks() {
        /* после первого опроса статус не может быть известен */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.INIT, gasBoilerService.getStatus());

        /* температура подачи растет - котел должен считаться работающим */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());

        /* температура подачи немного упала - котел все еще должен считаться работающим */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(46F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());

        /* температура подачи упала сильно - котел считается отключенным */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(40F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());

        /* температура подачи растет - котел должен считаться работающим */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(41F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());

        /* температура подачи растет - котел должен считаться работающим */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(42F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());

        /* температура подачи упала сильно - котел считается отключенным */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(34F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());
    }

    @Test
    @DisplayName("Проверка правильного расчета статуса котла при запуске (зима) если котел при запуске системы не работает")
    void checkStatusCalculationOnTemperatureOnStartWinterIdle() {
        /* после первого опроса статус не может быть известен */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.INIT, gasBoilerService.getStatus());

        /* температура подачи падает - котел считается отключенным */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(43F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());

        /* температура подачи растет - котел считается работающим */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(46F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());

        /* температура подачи немного упала - котел все еще должен считаться работающим */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());

        /* температура подачи упала сильно - котел считается отключенным */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(40F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());

        /* температура подачи растет - котел должен считаться работающим */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(41F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());
    }

    @Test
    @DisplayName("Проверка правильного расчета статуса котла летом (когда запроса на тепло нет)")
    void checkStatusCalculationOnTemperatureSummer() {
        /* если реле кота разомкнуто - котел не работает на отопление*/
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());

        /* температура подачи почти такая же - котел должен считаться отключенным */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(44.9F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());

        /* температура подачи почти такая же - котел должен считаться отключенным */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45.1F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());
    }

    private void setHeatRequestStatusField(GasBoilerHeatRequestStatus status) {
        try {
            Field field = gasBoilerService.getClass().getDeclaredField("heatRequestStatus");
            field.setAccessible(true);
            field.set(gasBoilerService, status);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось установить статус запроса на тепло газового котла", e);
        }
    }

    private void invokeManageGasBoilerRelayMethod(GasBoilerRelayStatus status) {
        try {
            if (status == GasBoilerRelayStatus.NO_NEED_HEAT) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                    .thenReturn(new boolean[]{true, false});
            }
            if (status == GasBoilerRelayStatus.NEED_HEAT) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                    .thenReturn(new boolean[]{false, false});
            }
            Method method = gasBoilerService.getClass().getDeclaredMethod("manageGasBoilerRelay");
            method.setAccessible(true);
            method.invoke(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод управления реле котла", e);
        }
    }

    @Test
    @DisplayName("Проверка правильного управления реле котла с учетом тактования")
    void checkManageBoilerRelay() throws ModbusException {
        setHeatRequestStatusField(GasBoilerHeatRequestStatus.NEED_HEAT);

        invokeManageGasBoilerRelayMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        /* вызываем метод снова, чтобы проверить, что реле не сработает второй раз */
        Mockito.clearInvocations(modbusService);
        invokeManageGasBoilerRelayMethod(GasBoilerRelayStatus.NEED_HEAT);
        Mockito.verify(modbusService, Mockito.times(0))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        /* имитируем рост температуры - включение котла */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        /* убираем запрос на тепло и убеждаемся, что реле отключится у */
        setHeatRequestStatusField(GasBoilerHeatRequestStatus.NO_NEED_HEAT);
        invokeManageGasBoilerRelayMethod(GasBoilerRelayStatus.NEED_HEAT);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        /* снова даем запрос и предполагаем, что котел отключился по подаче */
        setHeatRequestStatusField(GasBoilerHeatRequestStatus.NEED_HEAT);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(40F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        /* снова включаем котел и он не должен включиться по теплой обратке */
        setHeatRequestStatusField(GasBoilerHeatRequestStatus.NEED_HEAT);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeManageGasBoilerRelayMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        Mockito.verify(modbusService, Mockito.times(0))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        /* теперь даем обратке остыть и включение должно быть разрешено */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(25F);
        invokeManageGasBoilerRelayMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
    }

    private void invokePutGasBoilerStatusToDailyHistoryMethod(GasBoilerStatus calculatedStatus) {
        try {
            Method method = gasBoilerService.getClass()
                .getDeclaredMethod("putGasBoilerStatusToDailyHistory", GasBoilerStatus.class);
            method.setAccessible(true);
            method.invoke(gasBoilerService, calculatedStatus);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод добавления статуса газового котла в датасет", e);
        }
    }

    private Map<Instant, GasBoilerStatus> getGasBoilerStatusDailyHistory() {
        try {
            Field field = gasBoilerService.getClass().getDeclaredField("gasBoilerStatusDailyHistory");
            field.setAccessible(true);
            return (Map<Instant, GasBoilerStatus>) field.get(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось обратиться к датасету статусов котла", e);
        }
    }

    @Test
    @DisplayName("Проверка очистки старых записей статусов из датасета")
    void checkGasBoilerStatusHistoryClean() {
        Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = getGasBoilerStatusDailyHistory();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(25, ChronoUnit.HOURS), GasBoilerStatus.WORKS);
        invokePutGasBoilerStatusToDailyHistoryMethod(GasBoilerStatus.IDLE);
        assertEquals(1, gasBoilerStatusDailyHistory.size());
    }

    private Pair<List<Float>, List<Float>> invokeCalculateWorkIdleIntervalsMethod(Map<Instant, GasBoilerStatus> gasBoilerStatusHistory) {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateWorkIdleIntervals", Map.class);
            method.setAccessible(true);
            return (Pair<List<Float>, List<Float>>) method.invoke(gasBoilerService, gasBoilerStatusHistory);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета интервалов", e);
        }
    }

    private float invokeCalculateWorkPercentMethod(Pair<List<Float>, List<Float>> intervals) {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateWorkPercent", Pair.class);
            method.setAccessible(true);
            return (float) method.invoke(gasBoilerService, intervals);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета процента работы газового котла", e);
        }
    }

    @Test
    @DisplayName("Проверка расчета процента работы на отопление")
    void checkCalculateWorkPercent() {
        Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = getGasBoilerStatusDailyHistory();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(10, ChronoUnit.MINUTES), GasBoilerStatus.INIT);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(9, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(8, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(7, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(6, ChronoUnit.MINUTES), GasBoilerStatus.ERROR);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(5, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(4, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(3, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(2, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(1, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        assertEquals(33.333,
            invokeCalculateWorkPercentMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory)),
            0.001
        );

        gasBoilerStatusDailyHistory.clear();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(2, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(1, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        assertEquals(50,
            invokeCalculateWorkPercentMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory)),
            0.001
        );

        gasBoilerStatusDailyHistory.clear();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(2, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(1, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        assertEquals(50,
            invokeCalculateWorkPercentMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory)),
            0.001
        );

        gasBoilerStatusDailyHistory.clear();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(2, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(1, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        assertEquals(0,
            invokeCalculateWorkPercentMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory)),
            0.001
        );

        gasBoilerStatusDailyHistory.clear();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(2, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(1, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        assertEquals(100,
            invokeCalculateWorkPercentMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory)),
            0.001
        );
    }

    @Test
    @DisplayName("Проверка расчета процента работы на отопление на отсутствие NaN")
    void checkCalculateWorkPercentNan() {
        /* чтобы не выдавало "не число" когда первый опрос был менее минуты назад */
        Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = getGasBoilerStatusDailyHistory();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(30, ChronoUnit.SECONDS), GasBoilerStatus.IDLE);

        float workPercent =
            invokeCalculateWorkPercentMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory));

        DecimalFormat df0 = new DecimalFormat("#");
        assertEquals("0", df0.format(workPercent));
    }

    private Pair<Float, Float> invokeCalculateAverageTimesMethod(Pair<List<Float>, List<Float>> intervals) {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateAverageTimes", Pair.class);
            method.setAccessible(true);
            return (Pair<Float, Float>) method.invoke(gasBoilerService, intervals);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета среднего времени работы газового котла", e);
        }
    }

    @Test
    @DisplayName("Проверка расчета среднего времени работы на отопление")
    void checkCalculateAverageTimes() {
        Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = getGasBoilerStatusDailyHistory();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(10, ChronoUnit.MINUTES), GasBoilerStatus.INIT);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(9, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(8, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(7, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(6, ChronoUnit.MINUTES), GasBoilerStatus.ERROR);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(5, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(4, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(3, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(2, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(1, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        assertEquals(Pair.of(1f, 1.5f),
            invokeCalculateAverageTimesMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory))
        );

        gasBoilerStatusDailyHistory.clear();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(3, ChronoUnit.MINUTES), GasBoilerStatus.INIT);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(2, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(1, ChronoUnit.MINUTES), GasBoilerStatus.IDLE);
        assertEquals(Pair.of(0f, 2f),
            invokeCalculateAverageTimesMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory))
        );

        gasBoilerStatusDailyHistory.clear();
        gasBoilerStatusDailyHistory.put(Instant.now().minus(3, ChronoUnit.MINUTES), GasBoilerStatus.INIT);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(2, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        gasBoilerStatusDailyHistory.put(Instant.now().minus(1, ChronoUnit.MINUTES), GasBoilerStatus.WORKS);
        assertEquals(Pair.of(2f, 0f),
            invokeCalculateAverageTimesMethod(invokeCalculateWorkIdleIntervalsMethod(gasBoilerStatusDailyHistory))
        );
    }

    private float invokeCalculateAverageTemperatureDeltaWhenWorkMethod(
        Map<Instant, Float> gasBoilerDirectWhenWorkTemperatureHistory,
        Map<Instant, Float> gasBoilerReturnWhenWorkTemperatureHistory
    ) {
        try {
            Method method = gasBoilerService.getClass()
                .getDeclaredMethod("calculateAverageTemperatureDeltaWhenWork", Map.class, Map.class);
            method.setAccessible(true);
            return (float) method.invoke(gasBoilerService,
                gasBoilerDirectWhenWorkTemperatureHistory,
                gasBoilerReturnWhenWorkTemperatureHistory
            );
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета дельты при работе", e);
        }
    }

    private Map<Instant, Float> getGasBoilerDirectWhenWorkTemperatureToDailyHistory() {
        try {
            Field field =
                gasBoilerService.getClass().getDeclaredField("gasBoilerDirectWhenWorkTemperatureDailyHistory");
            field.setAccessible(true);
            return (Map<Instant, Float>) field.get(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось обратиться к датасету температур подачи при работе", e);
        }
    }

    private void invokePutGasBoilerDirectWhenWorkTemperatureToDailyHistory(Float temperature) {
        try {
            Method method = gasBoilerService.getClass()
                .getDeclaredMethod("putGasBoilerDirectWhenWorkTemperatureToDailyHistory", Float.class);
            method.setAccessible(true);
            method.invoke(gasBoilerService, temperature);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод добавления температуры подачи при работе газового котла в датасет",
                e
            );
        }
    }

    private Map<Instant, Float> getGasBoilerReturnWhenWorkTemperatureToDailyHistory() {
        try {
            Field field =
                gasBoilerService.getClass().getDeclaredField("gasBoilerReturnWhenWorkTemperatureDailyHistory");
            field.setAccessible(true);
            return (Map<Instant, Float>) field.get(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось обратиться к датасету температур обратки при работе", e);
        }
    }

    private void invokePutGasBoilerReturnWhenWorkTemperatureToDailyHistory(Float temperature) {
        try {
            Method method = gasBoilerService.getClass()
                .getDeclaredMethod("putGasBoilerReturnWhenWorkTemperatureToDailyHistory", Float.class);
            method.setAccessible(true);
            method.invoke(gasBoilerService, temperature);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод добавления температуры обратки при работе газового котла в датасет",
                e
            );
        }
    }

    @Test
    @DisplayName("Проверка очистки старых записей температур подачи при работе из датасета")
    void checkGasBoilerDirectWhenWorkTemperatureHistoryClean() {
        Map<Instant, Float> gasBoilerDirectWhenWorkTemperatureToDailyHistory =
            getGasBoilerDirectWhenWorkTemperatureToDailyHistory();
        gasBoilerDirectWhenWorkTemperatureToDailyHistory.put(Instant.now().minus(25, ChronoUnit.HOURS), 40f);
        invokePutGasBoilerDirectWhenWorkTemperatureToDailyHistory(30F);
        assertEquals(1, gasBoilerDirectWhenWorkTemperatureToDailyHistory.size());
    }

    @Test
    @DisplayName("Проверка очистки старых записей температур обратки при работе из датасета")
    void checkGasBoilerReturnWhenWorkTemperatureHistoryClean() {
        Map<Instant, Float> gasBoilerReturnWhenWorkTemperatureToDailyHistory =
            getGasBoilerReturnWhenWorkTemperatureToDailyHistory();
        gasBoilerReturnWhenWorkTemperatureToDailyHistory.put(Instant.now().minus(25, ChronoUnit.HOURS), 40f);
        invokePutGasBoilerReturnWhenWorkTemperatureToDailyHistory(30F);
        assertEquals(1, gasBoilerReturnWhenWorkTemperatureToDailyHistory.size());
    }

    @Test
    @DisplayName("Проверка расчета средней дельты при работе")
    void checkCalculateAverageTemperatureDeltaWhenWorks() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(40F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(30F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(41F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(36F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(42F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(37F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(44F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(39F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(36F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(30F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(40F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(35F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(39F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(34F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        assertEquals(5f, invokeCalculateAverageTemperatureDeltaWhenWorkMethod(
            getGasBoilerDirectWhenWorkTemperatureToDailyHistory(),
            getGasBoilerReturnWhenWorkTemperatureToDailyHistory()
        ));
    }

    private float invokeCalculateMinReturnTemperatureMethod() {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateMinReturnTemperature");
            method.setAccessible(true);
            return (float) method.invoke(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета целевой обратки газового котла для включения",
                e
            );
        }
    }

    @Test
    @DisplayName("Проверка расчета целевой обратки газового котла для включения")
    void checkCalculateMinReturnTemperatureMethod() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-30F);
        assertEquals(45f, invokeCalculateMinReturnTemperatureMethod());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(22F);
        assertEquals(25f, invokeCalculateMinReturnTemperatureMethod());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-19.9F);
        assertEquals(45f, invokeCalculateMinReturnTemperatureMethod(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(16.9F);
        assertEquals(25f, invokeCalculateMinReturnTemperatureMethod(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-1.5F);
        assertEquals(35f, invokeCalculateMinReturnTemperatureMethod(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-10F);
        assertEquals(40f, invokeCalculateMinReturnTemperatureMethod(), 0.5);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(10F);
        assertEquals(29f, invokeCalculateMinReturnTemperatureMethod(), 0.5);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-19F);
        assertEquals(44f, invokeCalculateMinReturnTemperatureMethod(), 0.5);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(16F);
        assertEquals(26f, invokeCalculateMinReturnTemperatureMethod(), 0.5);
    }
}

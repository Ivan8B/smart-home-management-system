package home.automation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.BypassRelayStatus;
import home.automation.enums.FloorHeatingStatus;
import home.automation.enums.GasBoilerHeatRequestStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.info.BypassRelayStatusCalculatedEvent;
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

    private void invokeCalculateStatusMethod() {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateStatus");
            method.setAccessible(true);
            method.invoke(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета статуса газового котла", e);
        }
    }

    @Test
    @DisplayName("Проверка правильного расчета статуса котла когда температура подачи растет")
    void checkStatusCalculationWorksOnTemperature() {
        /* в радиаторы нужно тепло */
        applicationEventPublisher.publishEvent(new BypassRelayStatusCalculatedEvent(this, BypassRelayStatus.CLOSED));
        /* состояние теплых полов неважно */
        Mockito.when(floorHeatingService.getStatus()).thenReturn(FloorHeatingStatus.NO_NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeCalculateStatusMethod();

        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());
    }

    @Test
    @DisplayName("Проверка правильного расчета статуса котла когда температура подачи падает")
    void checkStatusCalculationIdleOnTemperature() {
        /* в радиаторы нужно тепло */
        applicationEventPublisher.publishEvent(new BypassRelayStatusCalculatedEvent(this, BypassRelayStatus.CLOSED));
        /* состояние теплых полов неважно */
        Mockito.when(floorHeatingService.getStatus()).thenReturn(FloorHeatingStatus.NO_NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeCalculateStatusMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod();

        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());
    }

    private Duration invokeCalculateDelayBetweenTurnOnMethod() {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateDelayBetweenTurnOn");
            method.setAccessible(true);
            return (Duration) method.invoke(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета задержки включения котла", e);
        }
    }

    @Test
    @DisplayName("Проверка правильного управления реле котла с учетом тактования")
    void checkCalculateDelayBetweenTurnOn() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-40f);
        assertEquals(Duration.of(15, ChronoUnit.MINUTES), invokeCalculateDelayBetweenTurnOnMethod());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(0f);
        assertEquals(Duration.of(28, ChronoUnit.MINUTES), invokeCalculateDelayBetweenTurnOnMethod());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(40f);
        assertEquals(Duration.of(45, ChronoUnit.MINUTES), invokeCalculateDelayBetweenTurnOnMethod());
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

    private void setTurnOffTimestampField(Instant timestamp) {
        try {
            Field field = gasBoilerService.getClass().getDeclaredField("turnOffTimestamp");
            field.setAccessible(true);
            field.set(gasBoilerService, timestamp);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось установить время отключения газового котла", e);
        }
    }

    private void invokeManageBoilerRelayMethod() {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("manageBoilerRelay");
            method.setAccessible(true);
            method.invoke(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод управления реле котла", e);
        }
    }

    @Test
    @DisplayName("Проверка правильного управления реле котла с учетом тактования")
    void checkManageBoilerRelay() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE)).thenReturn(2f);

        setHeatRequestStatusField(GasBoilerHeatRequestStatus.NEED_HEAT);
        invokeManageBoilerRelayMethod();
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        /* вызываем метод снова, чтобы проверить, что реле не сработает второй раз */
        Mockito.clearInvocations(modbusService);
        invokeManageBoilerRelayMethod();
        Mockito.verify(modbusService, Mockito.times(0))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        /* имитируем рост температуры - включение котла */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod();
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeCalculateStatusMethod();

        /* убираем запрос на тепло */
        setHeatRequestStatusField(GasBoilerHeatRequestStatus.NO_NEED_HEAT);
        invokeManageBoilerRelayMethod();
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        Mockito.clearInvocations(modbusService);

        /* снова даем запрос и предполагаем, что котел отключился по подаче */
        setHeatRequestStatusField(GasBoilerHeatRequestStatus.NEED_HEAT);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeCalculateStatusMethod();
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod();

        /* снова включаем котел и он не должен включиться */
        setHeatRequestStatusField(GasBoilerHeatRequestStatus.NEED_HEAT);
        invokeManageBoilerRelayMethod();
        Mockito.verify(modbusService, Mockito.times(0))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        /* теперь подкручиваем время последнего выключения и он должен включиться */
        setTurnOffTimestampField(Instant.now().minus(29, ChronoUnit.MINUTES));
        invokeManageBoilerRelayMethod();
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

    private Pair<List<Float>, List<Float>> invokeCalculateWorkIdleIntervalsMethod() {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateWorkIdleIntervals");
            method.setAccessible(true);
            return (Pair<List<Float>, List<Float>>) method.invoke(gasBoilerService);
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
        assertEquals(33.333, invokeCalculateWorkPercentMethod(invokeCalculateWorkIdleIntervalsMethod()), 0.001);
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
        assertEquals(Pair.of(1f, 1.5f), invokeCalculateAverageTimesMethod(invokeCalculateWorkIdleIntervalsMethod()));
    }

    private float invokeCalculateAverageGasBoilerReturnTemperatureMethod() {
        try {
            Method method = gasBoilerService.getClass().getDeclaredMethod("calculateAverageGasBoilerReturnTemperature");
            method.setAccessible(true);
            return (float) method.invoke(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета средней температуры обратки при включении", e);
        }
    }

    private void invokePutGasBoilerReturnTemperatureToDailyHistoryMethod(Float temperature) {
        try {
            Method method = gasBoilerService.getClass()
                .getDeclaredMethod("putGasBoilerReturnTemperatureToDailyHistory", Float.class);
            method.setAccessible(true);
            method.invoke(gasBoilerService, temperature);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод добавления температуры обратки газового котла в датасет",
                e
            );
        }
    }

    private Map<Instant, Float> getGasBoilerReturnTemperatureHistory() {
        try {
            Field field = gasBoilerService.getClass().getDeclaredField("gasBoilerReturnTemperatureHistory");
            field.setAccessible(true);
            return (Map<Instant, Float>) field.get(gasBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось обратиться к датасету температур обратки", e);
        }
    }

    @Test
    @DisplayName("Проверка очистки старых записей температур обратки из датасета")
    void checkGasBoilerReturnTemperatureHistoryClean() {
        Map<Instant, Float> gasBoilerReturnTemperatureHistory = getGasBoilerReturnTemperatureHistory();
        gasBoilerReturnTemperatureHistory.put(Instant.now().minus(25, ChronoUnit.HOURS), 40f);
        invokePutGasBoilerReturnTemperatureToDailyHistoryMethod(30f);
        assertEquals(1, gasBoilerReturnTemperatureHistory.size());
    }

    @Test
    @DisplayName("Проверка правильного расчета средней температуры обратки")
    void checkCalculationAverageGasBoilerReturnTemperature() {
        /* в радиаторы нужно тепло */
        applicationEventPublisher.publishEvent(new BypassRelayStatusCalculatedEvent(this, BypassRelayStatus.CLOSED));
        /* состояние теплых полов неважно */
        Mockito.when(floorHeatingService.getStatus()).thenReturn(FloorHeatingStatus.NO_NEED_HEAT);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(40F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(35F);
        invokeCalculateStatusMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(40F);
        invokeCalculateStatusMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(41F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(38F);
        invokeCalculateStatusMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(46F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(41F);
        invokeCalculateStatusMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(42F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(39F);
        invokeCalculateStatusMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(42F);
        invokeCalculateStatusMethod();

        assertEquals(41.5f, invokeCalculateAverageGasBoilerReturnTemperatureMethod());
    }
}

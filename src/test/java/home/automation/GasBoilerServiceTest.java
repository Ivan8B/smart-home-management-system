package home.automation;

import java.lang.reflect.Method;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.GasBoilerRelayStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.HeatRequestStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.exception.ModbusException;
import home.automation.service.FloorHeatingService;
import home.automation.service.GasBoilerService;
import home.automation.service.HeatRequestService;
import home.automation.service.TemperatureSensorsService;
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

    @MockBean
    HeatRequestService heatRequestService;

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
        /* температуру обратки ставим пониже */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(20F);
        
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
        /* температуру обратки ставим пониже */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(20F);
        
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
    @DisplayName("Проверка правильного расчета статуса котла летом")
    void checkStatusCalculationOnTemperatureSummer() {
        /* температуру обратки ставим пониже */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(20F);

        /* если реле кота разомкнуто - котел не работает на отопление*/
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());

        /* температура подачи растет - а котел все еще выключен */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(44.9F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());

        /* температура подачи растет - а котел все еще выключен */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45.1F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NO_NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());
    }

    @Test
    @DisplayName("Проверка правильного расчета статуса котла когда он стоит в ошибке")
    void checkStatusCalculationOnTemperatureError() {
        /* температура подачи и обратки почти одинаковы */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE))
            .thenReturn(44F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.INIT, gasBoilerService.getStatus());

        /* температура подачи растет из-за колебаний погоды- а котел все еще выключен */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45.5F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());

        /* ошибку сбросили, температура подачи выросла сильнее и котел включился */
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(48F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        assertEquals(GasBoilerStatus.WORKS, gasBoilerService.getStatus());
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
        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NEED_HEAT);

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
        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NO_NEED_HEAT);
        invokeManageGasBoilerRelayMethod(GasBoilerRelayStatus.NEED_HEAT);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        /* снова даем запрос и предполагаем, что котел отключился по подаче */
        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NEED_HEAT);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(40F);
        invokeCalculateStatusMethod(GasBoilerRelayStatus.NEED_HEAT);

        /* снова включаем котел и он не должен включиться по теплой обратке */
        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NEED_HEAT);
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
        assertEquals(34.5f, invokeCalculateMinReturnTemperatureMethod(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-10F);
        assertEquals(39.5f, invokeCalculateMinReturnTemperatureMethod(), 0.5);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(10F);
        assertEquals(27.8f, invokeCalculateMinReturnTemperatureMethod(), 0.5);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-19F);
        assertEquals(44f, invokeCalculateMinReturnTemperatureMethod(), 0.5);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(16F);
        assertEquals(25f, invokeCalculateMinReturnTemperatureMethod(), 0.5);
    }

    @Test
    @DisplayName("Проверка расчета целевой подачи газового котла")
    void checkCalculateTargetDirectTemperatureMethod() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-30F);
        assertEquals(71f, gasBoilerService.calculateTargetDirectTemperature());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-10F);
        assertEquals(60f,  gasBoilerService.calculateTargetDirectTemperature(), 1f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(0F);
        assertEquals(47f,  gasBoilerService.calculateTargetDirectTemperature(), 1f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(10F);
        assertEquals(36f,  gasBoilerService.calculateTargetDirectTemperature(), 1f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(16F);
        assertEquals(30f,  gasBoilerService.calculateTargetDirectTemperature());
    }
}

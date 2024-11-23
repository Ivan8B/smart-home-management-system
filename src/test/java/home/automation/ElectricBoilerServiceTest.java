package home.automation;

import home.automation.configuration.ElectricBoilerConfiguration;
import home.automation.enums.ElectricBoilerStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.HeatRequestStatus;
import home.automation.enums.HeatingPumpsStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.exception.ModbusException;
import home.automation.service.ElectricBoilerService;
import home.automation.service.GasBoilerService;
import home.automation.service.HeatRequestService;
import home.automation.service.HeatingPumpsService;
import home.automation.service.TemperatureSensorsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;

public class ElectricBoilerServiceTest extends AbstractTest {
    @Autowired
    ElectricBoilerService electricBoilerService;

    @Autowired
    ElectricBoilerConfiguration configuration;

    @MockBean
    TemperatureSensorsService temperatureSensorsService;

    @MockBean
    HeatRequestService heatRequestService;

    @MockBean
    HeatingPumpsService heatingPumpsService;

    @MockBean
    GasBoilerService gasBoilerService;

    private void invokeScheduledMethod(ElectricBoilerStatus status) {
        try {
            if (status == ElectricBoilerStatus.TURNED_ON) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                        .thenReturn(new boolean[]{true, false});
            }
            if (status == ElectricBoilerStatus.TURNED_OFF) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                        .thenReturn(new boolean[]{false, false});
            }
            Method method = electricBoilerService.getClass().getDeclaredMethod("control");
            method.setAccessible(true);
            method.invoke(electricBoilerService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод управления электрическим котлом", e);
        }
    }

    @Test
    @DisplayName("Проверка включения электрокотла при низкой температуре в котельной и наличии запроса на тепло")
    void checkEnable() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE))
                .thenReturn(TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimumTemperature() - 1F);
        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NEED_HEAT);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), true);
    }

    @Test
    @DisplayName("Проверка не включения электрокотла при низкой температуре в котельной и отсутствии запроса на " +
            "тепло, неработающих насосах или включенном газовом котле")
    void checkNotEnable() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE))
                .thenReturn(TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimumTemperature() - 1F);
        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NO_NEED_HEAT);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NEED_HEAT);
        Mockito.when(heatingPumpsService.getStatus()).thenReturn(HeatingPumpsStatus.TURNED_OFF);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        Mockito.when(heatingPumpsService.getStatus()).thenReturn(HeatingPumpsStatus.TURNED_ON);
        Mockito.when(gasBoilerService.getStatus()).thenReturn(GasBoilerStatus.WORKS);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), true);
    }

    @Test
    @DisplayName("Проверка отключения электрокотла при высокой температуре в котельной")
    void checkDisableTemperature() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE))
                .thenReturn(
                        TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimumTemperature() +
                                configuration.getHysteresis()
                                +
                                0.1F);
        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NEED_HEAT);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
    }

    @Test
    @DisplayName("Проверка отключения электрокотла при отсутствии запроса на тепло, отключении насосов или включении " +
            "газового котла")
    void checkDisableHeatRequest() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE))
                .thenReturn(
                        TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimumTemperature() +
                                configuration.getHysteresis()
                                -
                                0.1F);
        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NO_NEED_HEAT);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
        Mockito.clearInvocations(modbusService);

        Mockito.when(heatRequestService.getStatus()).thenReturn(HeatRequestStatus.NEED_HEAT);
        Mockito.when(heatingPumpsService.getStatus()).thenReturn(HeatingPumpsStatus.TURNED_OFF);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
        Mockito.clearInvocations(modbusService);

        Mockito.when(heatingPumpsService.getStatus()).thenReturn(HeatingPumpsStatus.TURNED_ON);
        Mockito.when(gasBoilerService.getStatus()).thenReturn(GasBoilerStatus.WORKS);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
    }

    @Test
    @DisplayName("Проверка того, что реле не переключается при температуре выше минимальной, но ниже гистерезиса")
    void checkHysteresis() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE))
                .thenReturn(TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimumTemperature() + 0.1F);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.never()).writeCoil(any(int.class), any(int.class), any(boolean.class));

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE))
                .thenReturn(TemperatureSensor.BOILER_ROOM_TEMPERATURE.getMinimumTemperature() + 0.1F);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.never()).writeCoil(any(int.class), any(int.class), any(boolean.class));
    }

    @Test
    @DisplayName("Проверка того, что реле не переключается при ошибке датчика температуры")
    void checkError() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE))
                .thenReturn(null);
        invokeScheduledMethod(ElectricBoilerStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.never()).writeCoil(any(int.class), any(int.class), any(boolean.class));
    }
}

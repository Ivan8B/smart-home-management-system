package home.automation;

import java.lang.reflect.Method;

import home.automation.configuration.HeatRequestConfiguration;
import home.automation.enums.HeatRequestStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.info.HeatRequestCalculatedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.HeatRequestService;
import home.automation.service.HeatingPumpsService;
import home.automation.service.TemperatureSensorsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.event.ApplicationEvents;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeatRequestServiceTest extends AbstractTest {
    @Autowired
    HeatRequestService heatRequestService;

    @Autowired
    HeatRequestConfiguration configuration;

    @MockBean
    TemperatureSensorsService temperatureSensorsService;

    @MockBean
    HeatingPumpsService heatingPumpsService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private void invokeScheduledMethod() {
        try {
            Method method = heatRequestService.getClass().getDeclaredMethod("control");
            method.setAccessible(true);
            method.invoke(heatRequestService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета статуса запроса на тепло в дом", e);
        }
    }

    @Test
    @DisplayName("Проверка наличия запроса на тепло в дом при низкой температуре на улице")
    void checkNeedHeat() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(0F);
        invokeScheduledMethod();
        assertEquals(
            1,
            applicationEvents.stream(HeatRequestCalculatedEvent.class)
                .filter(event -> HeatRequestStatus.NEED_HEAT.equals(event.getStatus())).count()
        );
        applicationEvents.clear();
    }

    @Test
    @DisplayName("Проверка отсутствия запроса на тепло в дом при низкой температуре на улице")
    void checkNoNeedHeat() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(24F);
        invokeScheduledMethod();
        assertEquals(
            1,
            applicationEvents.stream(HeatRequestCalculatedEvent.class)
                .filter(event -> HeatRequestStatus.NO_NEED_HEAT.equals(event.getStatus())).count()
        );
        applicationEvents.clear();
    }

    @Test
    @DisplayName("Проверка гистерезиса")
    void checkHysteresis() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(24F);
        invokeScheduledMethod();
        assertEquals(
            1,
            applicationEvents.stream(HeatRequestCalculatedEvent.class)
                .filter(event -> HeatRequestStatus.NO_NEED_HEAT.equals(event.getStatus())).count()
        );
        applicationEvents.clear();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(21F);
        invokeScheduledMethod();
        assertEquals(0, applicationEvents.stream(HeatRequestCalculatedEvent.class).count());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(0F);
        invokeScheduledMethod();
        assertEquals(
            1,
            applicationEvents.stream(HeatRequestCalculatedEvent.class)
                .filter(event -> HeatRequestStatus.NEED_HEAT.equals(event.getStatus())).count()
        );
        applicationEvents.clear();
    }
}
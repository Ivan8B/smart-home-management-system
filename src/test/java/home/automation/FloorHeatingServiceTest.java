package home.automation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import home.automation.configuration.FloorHeatingConfiguration;
import home.automation.enums.TemperatureSensor;
import home.automation.service.FloorHeatingService;
import home.automation.service.TemperatureSensorsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FloorHeatingServiceTest extends AbstractTest {

    @Autowired
    FloorHeatingService floorHeatingService;

    @Autowired
    FloorHeatingConfiguration floorHeatingConfiguration;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @MockBean
    TemperatureSensorsService temperatureSensorsService;

    private void invokeScheduledMethod() {
        try {
            Method method = floorHeatingService.getClass().getDeclaredMethod("control");
            method.setAccessible(true);
            method.invoke(floorHeatingService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод управления теплым полом", e);
        }
    }

    private Float getTargetDirectTemperature() {
        try {
            Field field = floorHeatingService.getClass().getDeclaredField("targetDirectTemperature");
            field.setAccessible(true);
            return (Float) field.get(floorHeatingService);
        } catch (Exception e) {
            throw new RuntimeException("Не получить целевую температуру", e);
        }
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов")
    void checkAverageTemperatureCalculation() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(20F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.SECOND_FLOOR_BATHROOM_TEMPERATURE))
            .thenReturn(23F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(10F);
        invokeScheduledMethod();
        assertEquals(30.225F, getTargetDirectTemperature());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(22F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.SECOND_FLOOR_BATHROOM_TEMPERATURE))
            .thenReturn(23F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-10F);
        invokeScheduledMethod();
        assertEquals(36.225F, getTargetDirectTemperature());
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов при граничных значениях")
    void checkAverageTemperatureCalculationLimits() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(20F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.SECOND_FLOOR_BATHROOM_TEMPERATURE))
            .thenReturn(23F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-20F);
        invokeScheduledMethod();
        assertEquals(40F, getTargetDirectTemperature());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(25F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.SECOND_FLOOR_BATHROOM_TEMPERATURE))
            .thenReturn(25F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(20F);
        invokeScheduledMethod();
        assertEquals(30F, getTargetDirectTemperature());
    }
}
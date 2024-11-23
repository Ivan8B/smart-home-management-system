package home.automation;

import home.automation.configuration.FloorHeatingTemperatureConfiguration;
import home.automation.enums.TemperatureSensor;
import home.automation.service.FloorHeatingService;
import home.automation.service.TemperatureSensorsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FloorHeatingServiceTest extends AbstractTest {
    @Autowired
    FloorHeatingService floorHeatingService;

    @Autowired
    FloorHeatingTemperatureConfiguration configuration;

    @MockBean
    TemperatureSensorsService temperatureSensorsService;

    private Float invokeCalculateTargetDirectTemperature() {
        try {
            Method method = floorHeatingService.getClass()
                    .getDeclaredMethod("calculateTargetDirectTemperature");
            method.setAccessible(true);
            return (Float) method.invoke(floorHeatingService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета целевой температуры теплых полов", e);
        }
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов")
    void checkTargetTemperatureCalculation() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
                .thenReturn(-20F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
                .thenReturn(22F);
        assertEquals(42F, invokeCalculateTargetDirectTemperature(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
                .thenReturn(-10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
                .thenReturn(22F);
        assertEquals(38F, invokeCalculateTargetDirectTemperature(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
                .thenReturn(0F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
                .thenReturn(22F);
        assertEquals(34F, invokeCalculateTargetDirectTemperature(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
                .thenReturn(10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
                .thenReturn(22F);
        assertEquals(30F, invokeCalculateTargetDirectTemperature(), 0.5f);
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов при граничных значениях")
    void checkTargetTemperatureCalculationLimits() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
                .thenReturn(20F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
                .thenReturn(20F);
        assertEquals(configuration.getDirectMinTemperature(), invokeCalculateTargetDirectTemperature());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
                .thenReturn(10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
                .thenReturn(-31F);
        assertEquals(configuration.getDirectMaxTemperature(), invokeCalculateTargetDirectTemperature());
    }
}

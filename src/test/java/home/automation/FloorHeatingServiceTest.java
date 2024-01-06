package home.automation;

import java.lang.reflect.Method;

import home.automation.configuration.FloorHeatingTemperatureConfiguration;
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
    FloorHeatingTemperatureConfiguration configuration;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    @MockBean
    TemperatureSensorsService temperatureSensorsService;

    private float invokeCalculateTargetDirectTemperature() {
        try {
            Method method = floorHeatingService.getClass()
                .getDeclaredMethod("calculateTargetDirectTemperature");
            method.setAccessible(true);
            return (float) method.invoke(floorHeatingService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета целевой температуры теплых полов", e);
        }
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов")
    void checkAverageTemperatureCalculation() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(21F);
        assertEquals(30F, invokeCalculateTargetDirectTemperature(), 0.5f);
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов при граничных значениях")
    void checkAverageTemperatureCalculationLimits() {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(configuration.getDirectMinTemperature());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-30F);
        assertEquals(configuration.getDirectMaxTemperature(), invokeCalculateTargetDirectTemperature());

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(23F);
        assertEquals(configuration.getDirectMinTemperature(), invokeCalculateTargetDirectTemperature(), 0.5f);
    }
}
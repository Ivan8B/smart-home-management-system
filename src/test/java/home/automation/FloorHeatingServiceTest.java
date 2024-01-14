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
    void checkTargetTemperatureCalculation() {
        /* ставим высокую температуру обратки чтобы не срезалась подача*/
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE))
            .thenReturn(32F);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(20F);
        assertEquals(30.5F, invokeCalculateTargetDirectTemperature(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(0F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(20F);
        assertEquals(34.5F, invokeCalculateTargetDirectTemperature(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(20F);
        assertEquals(37.5F, invokeCalculateTargetDirectTemperature(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(22F);
        assertEquals(30F, invokeCalculateTargetDirectTemperature(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-0F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(22F);
        assertEquals(32F, invokeCalculateTargetDirectTemperature(), 0.5f);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(22F);
        assertEquals(35.5F, invokeCalculateTargetDirectTemperature(), 0.5f);


        /* ставим низкую температуру обратки и проверяем как срезается подача*/
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE))
            .thenReturn(24F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(20F);
        assertEquals(31F, invokeCalculateTargetDirectTemperature(), 0.5f);
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов при граничных значениях")
    void checkTargetTemperatureCalculationLimits() {
        /* ставим высокую температуру обратки чтобы не срезалась подача*/
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_FLOOR_TEMPERATURE))
            .thenReturn(38F);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(10F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-31F);
        assertEquals(configuration.getDirectMaxTemperature(), invokeCalculateTargetDirectTemperature());


        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.CHILD_BATHROOM_TEMPERATURE))
            .thenReturn(25F);
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(22F);
        assertEquals(configuration.getDirectMinTemperature(), invokeCalculateTargetDirectTemperature());
    }
}

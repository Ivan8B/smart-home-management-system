package home.automation;

import java.lang.reflect.Method;

import home.automation.configuration.FloorHeatingTemperatureConfiguration;
import home.automation.service.FloorHeatingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FloorHeatingServiceTest extends AbstractTest {
    @Autowired
    FloorHeatingService floorHeatingService;

    @Autowired
    FloorHeatingTemperatureConfiguration floorHeatingConfiguration;

    @Autowired
    ApplicationEventPublisher applicationEventPublisher;

    private float invokeCalculateTargetDirectTemperature(float averageInternalTemperature, float outsideTemperature) {
        try {
            Method method = floorHeatingService.getClass()
                .getDeclaredMethod("calculateTargetDirectTemperature", float.class, float.class);
            method.setAccessible(true);
            return (float) method.invoke(floorHeatingService, averageInternalTemperature, outsideTemperature);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод расчета целевой температуры теплых полов", e);
        }
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов")
    void checkAverageTemperatureCalculation() {
        assertEquals(30F, invokeCalculateTargetDirectTemperature(21F, 10F));
    }

    @Test
    @DisplayName("Проверка правильности расчета целевой температуры теплых полов при граничных значениях")
    void checkAverageTemperatureCalculationLimits() {
        assertEquals(40F, invokeCalculateTargetDirectTemperature(20F, -20F));

        assertEquals(30F, invokeCalculateTargetDirectTemperature(20F, 23F));
    }
}
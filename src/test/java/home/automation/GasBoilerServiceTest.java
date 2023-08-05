package home.automation;

import java.lang.reflect.Method;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.BypassRelayStatus;
import home.automation.enums.FloorHeatingStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.BypassRelayStatusCalculatedEvent;
import home.automation.service.FloorHeatingService;
import home.automation.service.GasBoilerService;
import home.automation.service.TemperatureSensorsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private void invokeScheduledMethod() {
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
        invokeScheduledMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(47F);
        invokeScheduledMethod();

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
        invokeScheduledMethod();

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE))
            .thenReturn(45F);
        invokeScheduledMethod();

        assertEquals(GasBoilerStatus.IDLE, gasBoilerService.getStatus());
    }

}

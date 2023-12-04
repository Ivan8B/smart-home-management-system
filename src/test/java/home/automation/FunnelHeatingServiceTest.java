package home.automation;

import java.lang.reflect.Method;

import home.automation.configuration.FunnelHeatingConfiguration;
import home.automation.enums.FunnelHeatingStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.exception.ModbusException;
import home.automation.service.FunnelHeatingService;
import home.automation.service.TemperatureSensorsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.any;

public class FunnelHeatingServiceTest extends AbstractTest {
    @Autowired
    FunnelHeatingService funnelHeatingService;
    @Autowired
    FunnelHeatingConfiguration configuration;
    @MockBean
    TemperatureSensorsService temperatureSensorsService;

    private void invokeScheduledMethod(FunnelHeatingStatus status) {
        try {
            if (status == FunnelHeatingStatus.TURNED_ON) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                    .thenReturn(new boolean[]{false, true});
            }
            if (status == FunnelHeatingStatus.TURNED_OFF) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                    .thenReturn(new boolean[]{false, false});
            }
            Method method = funnelHeatingService.getClass().getDeclaredMethod("control");
            method.setAccessible(true);
            method.invoke(funnelHeatingService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод управления обогревом", e);
        }
    }

    @Test
    @DisplayName("Проверка отключения обогрева воронок при морозе и при теплой погоде")
    void checkDisable() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(-10F);
        invokeScheduledMethod(FunnelHeatingStatus.TURNED_ON);

        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(10F);
        invokeScheduledMethod(FunnelHeatingStatus.TURNED_ON);

        Mockito.verify(modbusService, Mockito.times(2))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
    }

    @Test
    @DisplayName("Проверка включения обогрева воронок")
    void checkEnable() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(0F);
        invokeScheduledMethod(FunnelHeatingStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), true);
    }

    @Test
    @DisplayName("Проверка того, что реле не переключается при ошибке датчика температуры")
    void checkError() throws ModbusException {
        Mockito.when(temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE))
            .thenReturn(null);
        invokeScheduledMethod(FunnelHeatingStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.never()).writeCoil(any(int.class), any(int.class), any(boolean.class));
    }
}

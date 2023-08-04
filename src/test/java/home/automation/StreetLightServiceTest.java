package home.automation;

import java.lang.reflect.Method;
import java.util.Calendar;

import home.automation.configuration.StreetLightConfiguration;
import home.automation.enums.StreetLightStatus;
import home.automation.exception.ModbusException;
import home.automation.service.StreetLightService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StreetLightServiceTest extends AbstractTest {

    @Autowired
    StreetLightService streetLightService;

    @Autowired
    StreetLightConfiguration configuration;

    private void invokeControlMethod(Calendar calendar) {
        try {
            Method method = streetLightService.getClass().getDeclaredMethod("control", Calendar.class);
            method.setAccessible(true);
            method.invoke(streetLightService, calendar);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод управления освещением", e);
        }
    }

    @Test
    @DisplayName("Проверка отключения освещения в светлое время суток")
    void checkDisable() throws ModbusException {
        Calendar dayTime = Calendar.getInstance();
        dayTime.set(2023, Calendar.JUNE, 22, 12, 0);
        invokeControlMethod(dayTime);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
        assertEquals(StreetLightStatus.TURNED_OFF, streetLightService.getStatus());
    }

    @Test
    @DisplayName("Проверка включения освещения в темное время суток")
    void checkEnable() throws ModbusException {
        Calendar nightTime = Calendar.getInstance();
        nightTime.set(2023, Calendar.JUNE, 22, 2, 0);
        invokeControlMethod(nightTime);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), true);
        assertEquals(StreetLightStatus.TURNED_ON, streetLightService.getStatus());
    }

    @Test
    @DisplayName("Проверка граничного условия - включения в сумерки вечером")
    void checkEnableEveningTwilight() throws ModbusException {
        Calendar dayTimeBefore = Calendar.getInstance();
        dayTimeBefore.set(2023, Calendar.JUNE, 22, 22, 19);
        invokeControlMethod(dayTimeBefore);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        assertEquals(StreetLightStatus.TURNED_OFF, streetLightService.getStatus());

        Calendar dayTimeAfter = Calendar.getInstance();
        dayTimeAfter.set(2023, Calendar.JUNE, 22, 22, 21);
        invokeControlMethod(dayTimeAfter);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        assertEquals(StreetLightStatus.TURNED_ON, streetLightService.getStatus());
    }

    @Test
    @DisplayName("Проверка граничного условия - отключения в сумерки утром")
    void checkDisableMorningTwilight() throws ModbusException {
        Calendar dayTimeBefore = Calendar.getInstance();
        dayTimeBefore.set(2023, Calendar.JUNE, 22, 2, 43);
        invokeControlMethod(dayTimeBefore);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        assertEquals(StreetLightStatus.TURNED_ON, streetLightService.getStatus());

        Calendar dayTimeAfter = Calendar.getInstance();
        dayTimeAfter.set(2023, Calendar.JUNE, 22, 2, 45);
        invokeControlMethod(dayTimeAfter);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        assertEquals(StreetLightStatus.TURNED_OFF, streetLightService.getStatus());
    }
}

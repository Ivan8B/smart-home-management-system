package home.automation;

import java.lang.reflect.Method;
import java.util.Calendar;

import home.automation.configuration.StreetLightConfiguration;
import home.automation.exception.ModbusException;
import home.automation.service.StreetLightService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

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
            throw new RuntimeException("Не удалось вызвать метод по расписанию");
        }
    }

    @Test
    @DisplayName("Проверка отключения освещения в светлое время суток")
    void checkDisable() throws ModbusException {
        Calendar dayTime = Calendar.getInstance();
        dayTime.set(2023, Calendar.JULY, 22, 12, 0);
        invokeControlMethod(dayTime);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
    }

    @Test
    @DisplayName("Проверка включения освещения в темное время суток")
    void checkEnable() throws ModbusException {
        Calendar nightTime = Calendar.getInstance();
        nightTime.set(2023, Calendar.DECEMBER, 22, 0, 0);
        invokeControlMethod(nightTime);
        Mockito.verify(modbusService, Mockito.times(1))
            .writeCoil(configuration.getAddress(), configuration.getCoil(), true);
    }
}

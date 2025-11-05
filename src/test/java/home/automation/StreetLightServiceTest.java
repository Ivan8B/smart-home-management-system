package home.automation;

import home.automation.configuration.StreetLightConfiguration;
import home.automation.enums.StreetLightStatus;
import home.automation.exception.ModbusException;
import home.automation.service.StreetLightService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.TimeZone;

public class StreetLightServiceTest extends AbstractTest {
    @Autowired
    StreetLightService streetLightService;

    @Autowired
    StreetLightConfiguration configuration;

    private void invokeScheduledMethod(Calendar calendar, StreetLightStatus status) {
        try {
            if (status == StreetLightStatus.TURNED_ON) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                        .thenReturn(new boolean[]{false, true});
            }
            if (status == StreetLightStatus.TURNED_OFF) {
                Mockito.when(modbusService.readAllCoilsFromZero(configuration.getAddress()))
                        .thenReturn(new boolean[]{false, false});
            }
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
        Calendar dayTime = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        dayTime.set(2023, Calendar.JUNE, 22, 12, 0);

        invokeScheduledMethod(dayTime, StreetLightStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        /* если уже выключено - не выключается повторно*/
        Mockito.clearInvocations(modbusService);
        invokeScheduledMethod(dayTime, StreetLightStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
    }

    @Test
    @DisplayName("Проверка включения освещения в темное время суток")
    void checkEnable() throws ModbusException {
        Calendar nightTime = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        nightTime.set(2023, Calendar.JUNE, 22, 2, 0);

        invokeScheduledMethod(nightTime, StreetLightStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        /* если уже включено - не включается повторно*/
        Mockito.clearInvocations(modbusService);
        invokeScheduledMethod(nightTime, StreetLightStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
    }

    @Test
    @DisplayName("Проверка граничного условия - включения после заката")
    void checkEnableEveningTwilight() throws ModbusException {
        Calendar dayTimeBefore = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        dayTimeBefore.set(2023, Calendar.JUNE, 22, 21, 18);
        invokeScheduledMethod(dayTimeBefore, StreetLightStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);

        Calendar dayTimeAfter = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        dayTimeAfter.set(2023, Calendar.JUNE, 22, 21, 20);
        invokeScheduledMethod(dayTimeAfter, StreetLightStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), true);
    }

    @Test
    @DisplayName("Проверка граничного условия - отключения после восхода")
    void checkDisableMorningTwilight() throws ModbusException {
        Calendar dayTimeBefore = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        dayTimeBefore.set(2023, Calendar.JUNE, 22, 3, 44);
        invokeScheduledMethod(dayTimeBefore, StreetLightStatus.TURNED_OFF);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), true);

        Calendar dayTimeAfter = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        dayTimeAfter.set(2023, Calendar.JUNE, 22, 3, 46);
        invokeScheduledMethod(dayTimeAfter, StreetLightStatus.TURNED_ON);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getAddress(), configuration.getCoil(), false);
    }
}

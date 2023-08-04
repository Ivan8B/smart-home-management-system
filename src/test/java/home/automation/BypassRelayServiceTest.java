package home.automation;

import java.lang.reflect.Method;

import home.automation.configuration.BypassRelayConfiguration;
import home.automation.enums.BypassRelayStatus;
import home.automation.event.error.BypassRelayPollErrorEvent;
import home.automation.event.error.BypassRelayStatusCalculatedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.BypassRelayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BypassRelayServiceTest extends AbstractTest {

    @Autowired
    BypassRelayService bypassRelayService;

    @Autowired
    BypassRelayConfiguration configuration;

    @Autowired
    private ApplicationEvents applicationEvents;

    private void invokeScheduledMethod() {
        try {
            Method method = bypassRelayService.getClass().getDeclaredMethod("pollBypassRelay");
            method.setAccessible(true);
            method.invoke(bypassRelayService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод опроса реле", e);
        }
    }

    @Test
    @DisplayName("Проверка статуса реле после серии опросов (замкнуто)")
    void checkClose() throws ModbusException {
        Mockito.when(modbusService.readAllDiscreteInputsFromZero(configuration.getAddress()))
            .thenReturn(new boolean[]{true});
        for (int i = 0; i < configuration.getPollCountInPeriod() + 1; i++) {
            invokeScheduledMethod();
        }
        assertEquals(BypassRelayStatus.CLOSED, bypassRelayService.getStatus());
        assertEquals(
            1,
            applicationEvents.stream(BypassRelayStatusCalculatedEvent.class)
                .filter(event -> BypassRelayStatus.CLOSED.equals(event.getStatus())).count()
        );
    }

    @Test
    @DisplayName("Проверка статуса реле после серии опросов (разомкнуто)")
    void checkOpen() throws ModbusException {
        Mockito.when(modbusService.readAllDiscreteInputsFromZero(configuration.getAddress()))
            .thenReturn(new boolean[]{false});
        for (int i = 0; i < configuration.getPollCountInPeriod() + 1; i++) {
            invokeScheduledMethod();
        }
        assertEquals(BypassRelayStatus.OPEN, bypassRelayService.getStatus());
        assertEquals(
            1,
            applicationEvents.stream(BypassRelayStatusCalculatedEvent.class)
                .filter(event -> BypassRelayStatus.OPEN.equals(event.getStatus())).count()
        );
    }

    @Test
    @DisplayName("Проверка статуса реле после ошибки опроса")
    void checkException() throws ModbusException {
        Mockito.when(modbusService.readAllDiscreteInputsFromZero(configuration.getAddress()))
            .thenThrow(new ModbusException());
        invokeScheduledMethod();
        assertEquals(BypassRelayStatus.ERROR, bypassRelayService.getStatus());
        // событие по подсчету статуса долетать не должно, оно и не обрабатывается сервисом котла
        assertEquals(
            0,
            applicationEvents.stream(BypassRelayStatusCalculatedEvent.class)
                .filter(event -> BypassRelayStatus.ERROR.equals(event.getStatus())).count()
        );
        assertEquals(1, applicationEvents.stream(BypassRelayPollErrorEvent.class).count());
    }

}
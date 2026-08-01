package home.automation;

import home.automation.configuration.WaterLeakConfiguration;
import home.automation.enums.WaterLeakSensor;
import home.automation.enums.WaterLeakStatus;
import home.automation.exception.ModbusException;
import home.automation.service.WaterLeakService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(properties = {
        "waterLeak.outputs.filterValvePowerDuration = PT0.1S",
        "waterLeak.outputs.cityValvePowerDuration = PT0.1S"
})
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class WaterLeakServiceTest extends AbstractTest {
    @Autowired
    WaterLeakService waterLeakService;

    @Autowired
    WaterLeakConfiguration configuration;

    private void stubSensorsPoll(boolean isLeak) throws ModbusException {
        int[] values = new int[WaterLeakSensor.values().length];
        Arrays.fill(values, 1);
        if (isLeak) {
            values[WaterLeakSensor.SENSOR_1.getRegisterOffset()] = 0;
        }
        Mockito.when(modbusService.readHoldingRegisters(
                configuration.getSensorsAddress(),
                configuration.getSensorsRegisterStart(),
                WaterLeakSensor.values().length
        )).thenReturn(values);
    }

    private void invokeControlMethod() throws ModbusException {
        try {
            Method method = waterLeakService.getClass().getDeclaredMethod("control");
            method.setAccessible(true);
            method.invoke(waterLeakService);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось вызвать метод контроля протечки", e);
        }
    }

    @Test
    @DisplayName("Проверка обнаружения протечки и перекрытия воды")
    void checkLeakDetected() throws ModbusException {
        stubSensorsPoll(true);
        invokeControlMethod();

        assertEquals(WaterLeakStatus.LEAK, waterLeakService.getStatus());
        Mockito.verify(botService).notify(Mockito.contains("АВАРИЯ: протечка воды"));
        Mockito.verify(botService).notify(Mockito.contains("Краны перекрыты, насос отключен"));

        /* насос отключен */
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getPumpRelayAddress(), configuration.getPumpRelayCoil(), true);

        /* краны закрыты: команда + подача питания на сервопривод */
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValveCommandRegister(), false);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValvePowerRegister(), true);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValvePowerRegister(), false);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getCityValveCommandRegister(), false);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getCityValvePowerRegister(), true);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getCityValvePowerRegister(), false);
    }

    @Test
    @DisplayName("Проверка сохранения аварийного статуса после высыхания датчиков до ручного сброса")
    void checkStatusKeepsLeakUntilManualReset() throws ModbusException {
        stubSensorsPoll(true);
        invokeControlMethod();
        assertEquals(WaterLeakStatus.LEAK, waterLeakService.getStatus());

        /* датчики высохли, но вода перекрыта и авария не сброшена вручную */
        Mockito.clearInvocations(modbusService);
        Mockito.clearInvocations(botService);
        stubSensorsPoll(false);
        invokeControlMethod();

        assertEquals(WaterLeakStatus.LEAK, waterLeakService.getStatus());
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(configuration.getPumpRelayAddress(), configuration.getPumpRelayCoil(), true);
        Mockito.verify(botService, Mockito.times(0)).notify(Mockito.anyString());
    }

    @Test
    @DisplayName("Проверка сброса аварийного статуса только по команде открытия кранов")
    void checkStatusResetAfterManualCommand() throws ModbusException {
        stubSensorsPoll(true);
        invokeControlMethod();
        assertEquals(WaterLeakStatus.LEAK, waterLeakService.getStatus());

        /* ручная команда открытия кранов и включения насоса */
        Mockito.clearInvocations(modbusService);
        waterLeakService.openAllValvesAndTurnOnPump();

        assertEquals(WaterLeakStatus.OK, waterLeakService.getStatus());
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getPumpRelayAddress(), configuration.getPumpRelayCoil(), false);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValveCommandRegister(), true);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValvePowerRegister(), true);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValvePowerRegister(), false);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getCityValveCommandRegister(), true);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getCityValvePowerRegister(), true);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getCityValvePowerRegister(), false);

        /* после сброса сухие датчики не возвращают аварию */
        Mockito.clearInvocations(modbusService);
        stubSensorsPoll(false);
        invokeControlMethod();
        assertEquals(WaterLeakStatus.OK, waterLeakService.getStatus());

        /* новая протечка снова обнаруживается */
        stubSensorsPoll(true);
        invokeControlMethod();
        assertEquals(WaterLeakStatus.LEAK, waterLeakService.getStatus());
    }

    @Test
    @DisplayName("Проверка перекрытия только кранов фильтров без затрагивания скважины и городского ввода")
    void checkCloseFilterValves() throws ModbusException {
        stubSensorsPoll(false);
        invokeControlMethod();
        assertEquals(WaterLeakStatus.OK, waterLeakService.getStatus());

        Mockito.clearInvocations(modbusService);
        waterLeakService.closeFilterValves();

        /* кран фильтров перекрыт: команда + подача и снятие питания */
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValveCommandRegister(), false);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValvePowerRegister(), true);
        Mockito.verify(modbusService, Mockito.times(1))
                .writeCoil(configuration.getOutputsAddress(), configuration.getFilterValvePowerRegister(), false);

        /* скважина и городской ввод не тронуты */
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(
                        Mockito.eq(configuration.getPumpRelayAddress()),
                        Mockito.eq(configuration.getPumpRelayCoil()),
                        Mockito.anyBoolean()
                );
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(
                        Mockito.eq(configuration.getOutputsAddress()),
                        Mockito.eq(configuration.getCityValveCommandRegister()),
                        Mockito.anyBoolean()
                );
        Mockito.verify(modbusService, Mockito.times(0))
                .writeCoil(
                        Mockito.eq(configuration.getOutputsAddress()),
                        Mockito.eq(configuration.getCityValvePowerRegister()),
                        Mockito.anyBoolean()
                );

        /* статус не меняется */
        assertEquals(WaterLeakStatus.OK, waterLeakService.getStatus());
    }

    @Test
    @DisplayName("Проверка установки статуса ошибки при сбое опроса датчиков")
    void checkErrorStatusOnPollFailure() throws ModbusException {
        Mockito.when(modbusService.readHoldingRegisters(
                configuration.getSensorsAddress(),
                configuration.getSensorsRegisterStart(),
                WaterLeakSensor.values().length
        )).thenThrow(new ModbusException("Ошибка опроса датчиков протечки"));
        invokeControlMethod();

        assertEquals(WaterLeakStatus.ERROR, waterLeakService.getStatus());
    }
}

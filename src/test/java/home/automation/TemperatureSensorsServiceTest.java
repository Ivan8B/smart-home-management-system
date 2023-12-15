package home.automation;

import home.automation.configuration.TemperatureSensorsBoardsConfiguration;
import home.automation.enums.TemperatureSensor;
import home.automation.exception.ModbusException;
import home.automation.service.TemperatureSensorsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class TemperatureSensorsServiceTest extends AbstractTest {

    @Autowired
    TemperatureSensorsBoardsConfiguration configuration;

    @Autowired
    TemperatureSensorsService temperatureSensorsService;

    @Test
    @DisplayName("Проверка кэширования и протухания значений ")
    void checkCacheAndExpire() throws ModbusException, InterruptedException {
        Integer outsideTemperatureBoardAddress =
            configuration.getAddressByName(TemperatureSensor.OUTSIDE_TEMPERATURE.getBoardName());
        Integer outsideTemperatureRegisterId = TemperatureSensor.OUTSIDE_TEMPERATURE.getRegisterId();

        Mockito.when(modbusService.readHoldingRegister(outsideTemperatureBoardAddress, outsideTemperatureRegisterId))
            .thenReturn(100);

        assertEquals(10F,
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE)
        );

        Mockito.when(modbusService.readHoldingRegister(outsideTemperatureBoardAddress, outsideTemperatureRegisterId))
            .thenReturn(200);

        /* проверяем, что значение берется из кэша */
        assertEquals(10F,
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE)
        );

        /* проверяем что сервис ходил за температурой один раз */
        Mockito.verify(modbusService, Mockito.times(1))
            .readHoldingRegister(outsideTemperatureBoardAddress, outsideTemperatureRegisterId);

        /* ждем пока кэш протухнет */
        Thread.sleep(1100);

        /* проверяем, что взялось новое значение */
        assertEquals(20F,
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE)
        );

        /* проверяем что сервис сходил за температурой второй раз */
        Mockito.verify(modbusService, Mockito.times(2))
            .readHoldingRegister(outsideTemperatureBoardAddress, outsideTemperatureRegisterId);
    }

    @Test
    @DisplayName("Проверка что значения в кэше не путаются")
    void checkCachingConsistency() throws ModbusException {
        Integer outsideTemperatureBoardAddress =
            configuration.getAddressByName(TemperatureSensor.OUTSIDE_TEMPERATURE.getBoardName());
        Integer outsideTemperatureRegisterId = TemperatureSensor.OUTSIDE_TEMPERATURE.getRegisterId();

        Integer boilerRoomTemperatureBoardAddress =
            configuration.getAddressByName(TemperatureSensor.BOILER_ROOM_TEMPERATURE.getBoardName());
        Integer boilerRoomTemperatureRegisterId = TemperatureSensor.BOILER_ROOM_TEMPERATURE.getRegisterId();

        Mockito.when(modbusService.readHoldingRegister(outsideTemperatureBoardAddress, outsideTemperatureRegisterId))
            .thenReturn(100);

        Mockito.when(modbusService.readHoldingRegister(boilerRoomTemperatureBoardAddress,
            boilerRoomTemperatureRegisterId
        )).thenReturn(200);

        assertEquals(10F,
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE)
        );

        assertEquals(20F,
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE)
        );

        Mockito.when(modbusService.readHoldingRegister(outsideTemperatureBoardAddress, outsideTemperatureRegisterId))
            .thenReturn(200);

        Mockito.when(modbusService.readHoldingRegister(boilerRoomTemperatureBoardAddress,
            boilerRoomTemperatureRegisterId
        )).thenReturn(100);

        /* проверяем, что значение берется из кэша */
        assertEquals(10F,
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE)
        );
        assertEquals(20F,
            temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.BOILER_ROOM_TEMPERATURE)
        );

        /* проверяем что сервис ходил за температурой один раз */
        Mockito.verify(modbusService, Mockito.times(1))
            .readHoldingRegister(outsideTemperatureBoardAddress, outsideTemperatureRegisterId);
        Mockito.verify(modbusService, Mockito.times(1))
            .readHoldingRegister(boilerRoomTemperatureBoardAddress, boilerRoomTemperatureRegisterId);
    }
}
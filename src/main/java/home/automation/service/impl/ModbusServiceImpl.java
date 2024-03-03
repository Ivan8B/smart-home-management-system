package home.automation.service.impl;

import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import home.automation.configuration.ModbusConfiguration;
import home.automation.exception.ModbusException;
import home.automation.service.ModbusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class ModbusServiceImpl implements ModbusService {
    private static final Logger logger = LoggerFactory.getLogger(ModbusServiceImpl.class);
    private final ModbusConfiguration modbusConfiguration;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ModbusMaster modbusMaster;

    public ModbusServiceImpl(ModbusConfiguration modbusConfiguration) {
        this.modbusConfiguration = modbusConfiguration;
        try {
            init();
        } catch (ModbusException e) {
            /* тут ничего не делаем - в логи уже отписали */
            /* а перехватываем чтобы приложение не падало при запуске если связи нет */
        }
    }

    private void init() throws ModbusException {
        try {
            if (modbusMaster == null) {
                TcpParameters tcpParameters = new TcpParameters();
                tcpParameters.setHost(InetAddress.getByName(modbusConfiguration.getHost()));
                tcpParameters.setKeepAlive(true);
                tcpParameters.setPort(modbusConfiguration.getPort());

                modbusMaster = ModbusMasterFactory.createModbusMasterTCP(tcpParameters);
                Modbus.setAutoIncrementTransactionId(true);
            }

            modbusMaster.connect();

        } catch (Exception e) {
            logger.error("Ошибка подключения к modbus", e);
            throw new ModbusException();
        }
    }

    @Override
    public boolean[] readAllDiscreteInputsFromZero(int address) throws ModbusException {
        try {
            init();
            Future<boolean[]> future = executorService.submit(() -> readAllDiscreteInputsFromZeroWithDelay(address));
            return future.get();
        } catch (Exception e) {
            logger.error("Ошибка чтения состояния входов", e);
            throw new ModbusException();
        }
    }

    private boolean[] readAllDiscreteInputsFromZeroWithDelay(int address) throws Exception {
        boolean[] result = modbusMaster.readDiscreteInputs(address, 0, 1);
        delay();
        return result;
    }

    @Override
    public boolean[] readAllCoilsFromZero(int address) throws ModbusException {
        try {
            init();
            Future<boolean[]> future = executorService.submit(() -> readAllCoilsFromZeroWithDelay(address));
            return future.get();
        } catch (Exception e) {
            logger.error("Ошибка чтения состояний катушек", e);
            throw new ModbusException();
        }
    }

    private boolean[] readAllCoilsFromZeroWithDelay(int address) throws Exception {
        boolean[] result = modbusMaster.readCoils(address, 0, 1);
        delay();
        return result;
    }

    @Override
    public void writeCoil(int address, int coilId, boolean value) throws ModbusException {
        try {
            init();
            Future<boolean[]> future = executorService.submit(() -> writeCoilWithDelay(address, coilId, value));
            /* этот future.get нужен только чтобы получить ExecutionException и по нему понять, что что-то не так с
            записью*/
            future.get();
        } catch (Exception e) {
            logger.error("Ошибка выставления значения катушки", e);
            throw new ModbusException();
        }
    }

    private boolean[] writeCoilWithDelay(int address, int coilId, boolean value) throws Exception {
        modbusMaster.writeSingleCoil(address, coilId, value);
        delay();
        return new boolean[]{true};
    }

    @Override
    public int readHoldingRegister(int address, int registerId) throws ModbusException {
        try {
            init();
            Future<int[]> future = executorService.submit(() -> readHoldingRegistersWithDelay(address, registerId));
            return future.get()[0];
        } catch (Exception e) {
            logger.error("Ошибка чтения регистра", e);
            throw new ModbusException();
        }
    }

    private int[] readHoldingRegistersWithDelay(int address, int registerId) throws Exception {
        int[] result = modbusMaster.readHoldingRegisters(address, registerId, 1);
        delay();
        return result;
    }

    @Override
    public void writeHoldingRegister(int address, int registerId, int value) throws ModbusException {
        try {
            init();
            Future<int[]> future = executorService.submit(() -> writeHoldingRegistersWithDelay(address,
                    registerId,
                    value));
            /* этот future.get нужен только чтобы получить ExecutionException и по нему понять, что что-то не так с
            записью*/
            future.get();
        } catch (Exception e) {
            logger.error("Ошибка записи в регистр", e);
            throw new ModbusException();
        }
    }

    private int[] writeHoldingRegistersWithDelay(Integer address, Integer registerId, int value) throws Exception {
        modbusMaster.writeSingleRegister(address, registerId, value);
        delay();
        return new int[0];
    }

    private void delay() {
        try {
            Thread.sleep(modbusConfiguration.getDelay());
        } catch (InterruptedException ignored) {
        }
    }
}

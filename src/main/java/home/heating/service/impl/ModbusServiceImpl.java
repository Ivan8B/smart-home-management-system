package home.heating.service.impl;

import java.net.InetAddress;

import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.master.ModbusMaster;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import home.heating.configuration.ModbusConfiguration;
import home.heating.exception.ModbusException;
import home.heating.service.ModbusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ModbusServiceImpl implements ModbusService {
    private static final Logger logger = LoggerFactory.getLogger(ModbusServiceImpl.class);

    private ModbusMaster modbusMaster;

    private final ModbusConfiguration modbusConfiguration;

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
                tcpParameters.setHost(InetAddress.getByName(modbusConfiguration.getHOST()));
                tcpParameters.setKeepAlive(true);
                tcpParameters.setPort(modbusConfiguration.getPORT());

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
            return modbusMaster.readDiscreteInputs(address, 0, 1);
        } catch (Exception e) {
            logger.error("Ошибка чтения состояния входов", e);
            throw new ModbusException();
        }
    }

    @Override
    public boolean[] readAllCoilsFromZero(int address) throws ModbusException {
        try {
            init();
            return modbusMaster.readCoils(address, 0, 1);
        } catch (Exception e) {
            logger.error("Ошибка чтения состояний катушек", e);
            throw new ModbusException();
        }
    }

    @Override
    public void writeCoil(int address, int coilId, boolean value) throws ModbusException {
        try {
            init();
            modbusMaster.writeSingleCoil(address, coilId, value);
        } catch (Exception e) {
            logger.error("Ошибка выставления значения катушки", e);
            throw new ModbusException();
        }
    }

    @Override
    public int readHoldingRegister(int address, int registerId) throws ModbusException {
        try {
            init();
            int[] registerValues = modbusMaster.readHoldingRegisters(address, registerId, 1);
            return registerValues[0];
        } catch (Exception e) {
            logger.error("Ошибка чтения регистра", e);
            throw new ModbusException();
        }
    }
}

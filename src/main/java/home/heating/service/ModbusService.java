package home.heating.service;

import home.heating.exception.ModbusException;

public interface ModbusService {
    /**
     * Метод получения состояния дискретных входов реле, которые возвращают массив состояния при запросе к нулевому входу
     * @param address modbus адрес реле
     * @return массив состояний входов
     * @throws ModbusException
     */
    boolean[] readAllDiscreteInputsFromZero(int address) throws ModbusException;

    /**
     * Метод получения состояния катушек для реле, которые возвращают массив состояния при запросе к нулевой катушке
     * @param address modbus адрес реле
     * @return массив состояний катушек
     * @throws ModbusException
     */
    boolean[] readAllCoilsFromZero(int address) throws ModbusException;

    /**
     * Метод переключения состояния катушки
     * @param address modbus адрес реле
     * @param coilId id катушки
     * @param value новое значение катушки
     * @throws ModbusException
     */
    void writeCoil(int address, int coilId, boolean value) throws ModbusException;

    /**
     * Метод чтения состосяния Holding Register (F03)
     * @param address modbus адрес
     * @param registerId id регистра
     * @return одиночное значение
     * @throws ModbusException
     */
    int readHoldingRegister(int address, int registerId) throws ModbusException;
}

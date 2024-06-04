package home.automation.service;

import home.automation.exception.ModbusException;

public interface ModbusService {
    /**
     * Метод получения состояния дискретных входов реле, которые возвращают массив состояния при запросе к нулевому
     * входу
     *
     * @param address modbus адрес реле
     * @return массив состояний входов
     * @throws ModbusException
     */
    boolean[] readAllDiscreteInputsFromZero(int address) throws ModbusException;

    /**
     * Метод получения состояния катушек для реле, которые возвращают массив состояния при запросе к нулевой катушке
     *
     * @param address modbus адрес реле
     * @return массив состояний катушек
     * @throws ModbusException
     */
    boolean[] readAllCoilsFromZero(int address) throws ModbusException;

    /**
     * Метод переключения состояния катушки
     *
     * @param address modbus адрес реле
     * @param coilId  id катушки
     * @param value   новое значение катушки
     */
    void writeCoil(int address, int coilId, boolean value) throws ModbusException;

    /**
     * Метод чтения состояния Holding Register (F03)
     *
     * @param address    modbus адрес
     * @param registerId id регистра
     * @return одиночное значение
     */
    int readHoldingRegister(int address, int registerId) throws ModbusException;

    /**
     * Метод чтения состояния нескольких Holding Register (F03)
     *
     * @param address    modbus адрес
     * @param registerStartId id регистра
     * @param quantity количество регистров
     * @return массив значений
     */
    int[] readHoldingRegisters(int address, int registerStartId, int quantity) throws ModbusException;

    /**
     * Метод записи в Holding Register (F06)
     *
     * @param address    modbus адрес
     * @param registerId id регистра
     * @param value      значение
     */
    void writeHoldingRegister(int address, int registerId, int value) throws ModbusException;
}

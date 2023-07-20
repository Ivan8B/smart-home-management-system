package home.heating.exception;

public class ModbusException extends Exception {
    public ModbusException(String message) {
        super(message);
    }

    public ModbusException() {
        super();
    }
}

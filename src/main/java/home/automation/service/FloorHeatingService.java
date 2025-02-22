package home.automation.service;

public interface FloorHeatingService {

    /**
     * Получение статус теплых полов
     *
     * @return статус теплых полов
     */
    String getFormattedStatus();

    /**
     * Калибровка сервопривода теплых полов (перемещение по фиксированным положениям)
     *
     */
    void calibrate();
}

package home.automation.service;

import java.time.Instant;

import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;

public interface HistoryService {
    /**
     * Добавление статуса котла в историю
     * @param status статус котла
     * @param ts     время
     */
    void putGasBoilerStatusToDailyHistory(GasBoilerStatus status, Instant ts);

    /**
     * Добавление температуры в историю
     * @param sensor      датчик
     * @param temperature значение
     * @param ts          время
     */
    void putTemperatureToDailyHistory(TemperatureSensor sensor, Float temperature, Instant ts);

    /**
     * Получение аналитики по работе газового котла за прошедшие сутки
     *
     * @return сообщение для бота
     */
    String getGasBoilerFormattedStatusForLastDay();

    /**
     * Добавление рассчитанного процента открытия клапана в историю
     * @param calculatedTargetValvePercent рассчтитонное положение клапана
     * @param ts время
     */
    void putCalculatedTargetValvePercent(Integer calculatedTargetValvePercent, Instant ts);

    /**
     * Получение среднего рассчитанного процента открытия клапана за последний час
     * @return среднее рассчитанное положение клапана за последний час
     */
    Integer getAverageCalculatedTargetValvePercentForLastHour();
}

package home.automation.service;

import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;

import java.time.Duration;
import java.time.Instant;

public interface HistoryService {
    /**
     * Добавление статуса котла в историю
     *
     * @param status статус котла
     * @param ts     время
     */
    void putGasBoilerStatusToDailyHistory(GasBoilerStatus status, Instant ts);

    /**
     * Добавление температуры в историю
     *
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
     * Получение длительности периода текущего состояния газового котла
     *
     * @return длительность текущего состояния
     */
    Duration getGasBoilerCurrentStatusDuration();

    /**
     * Добавление рассчитанного процента открытия клапана в историю
     *
     * @param calculatedTargetValvePercent рассчтитонное положение клапана
     * @param ts                           время
     */
    void putCalculatedTargetValvePercent(Integer calculatedTargetValvePercent, Instant ts);

    /**
     * Получение среднего рассчитанного процента открытия клапана за последние N расчетов
     *
     * @return среднее рассчитанное положение клапана
     */
    Integer getAverageCalculatedTargetValvePercentForLastNValues();

    /**
     * Получение последнего рассчитанного процента открытия клапана за последние N расчетов
     *
     * @return последнее рассчитанное положение клапана
     */
    Integer getLastCalculatedTargetValvePercent();
}

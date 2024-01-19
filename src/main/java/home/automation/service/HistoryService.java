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
     * Расчет стабильности работы газового котла за последний час
     * @return true если газовый котел был непрерывно включен
     */
    boolean gasBoilerWorksStableLastHour();

    /**
     * Расчет средней температуры подачи в узел подмеса теплых полов во время работы котла за последний час
     * @return средняя температура или null если нет данных (или котел не работал)
     */
    Float getAverageFloorDirectBeforeMixingTemperatureWhenGasBoilerWorksForLastHour();
}

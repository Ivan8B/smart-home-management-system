package home.automation.service;

import java.time.Duration;
import java.time.Instant;

import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import org.jetbrains.annotations.Nullable;

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
     * Получить среднее значение подачи в теплые полы до смешения за последний час (в интервалы когда котел работал)
     */
    @Nullable Float getAverageFloorDirectTemperatureBeforeMixingWhenGasBoilerWorksForLastHourIfHasFullData();

    /**
     * Получение аналитики по работе котла за прошедшие сутки
     *
     * @return сообщение для бота
     */
    String getGasBoilerFormattedStatusForLastDay();
}

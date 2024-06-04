package home.automation.service;

import home.automation.enums.UniversalSensor;
import jakarta.annotation.Nullable;

public interface UniversalSensorsService {
    /**
     * Возвращает температуру в градусах по универсальному датчику
     * Если произошла ошибка опроса - возвращает null
     *
     * @param sensor датчик
     * @return температура с плавающей точкой
     */
    @Nullable
    Float getCurrentTemperatureForSensor(UniversalSensor sensor);

    /**
     * Возвращает влажность в процентах по универсальному датчику
     * Если произошла ошибка опроса - возвращает null
     *
     * @param sensor датчик
     * @return процент влажности
     */
    @Nullable
    Integer getCurrentHumidityPercentForSensor(UniversalSensor sensor);

    /**
     * Возвращает количество углекислого газа в ppm по универсальному датчику
     * Если произошла ошибка опроса - возвращает null
     *
     * @param sensor датчик
     * @return CO2 ppm
     */
    @Nullable
    Integer getCurrentCO2ppmForSensor(UniversalSensor sensor);

    /**
     * Возвращает форматированные показания всех универсальных датчиков
     *
     * @return форматированная строка
     */
    String getCurrentParamsFormatted();
}



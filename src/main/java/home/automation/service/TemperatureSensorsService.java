package home.automation.service;

import home.automation.enums.TemperatureSensor;
import jakarta.annotation.Nullable;

public interface TemperatureSensorsService {
    /**
     * Возвращает температуру в градусах по температурному датчику
     * Если произошла ошибка опроса - возвращает null
     *
     * @param sensor датчик
     * @return температура с плавающей точкой
     */
    @Nullable
    Float getCurrentTemperatureForSensor(TemperatureSensor sensor);

    /**
     * Возвращает форматированный результат опроса всех температурных датчиков
     *
     * @return форматированная строка
     */
    String getCurrentTemperaturesFormatted();
}

package home.automation.service.impl;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.GasBoilerRelayStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.HeatRequestStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.GasBoilerErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.GasBoilerService;
import home.automation.service.HeatRequestService;
import home.automation.service.HistoryService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.Instant;

@Service
public class GasBoilerServiceImpl implements GasBoilerService {
    private static final Logger logger = LoggerFactory.getLogger(GasBoilerServiceImpl.class);
    private final GasBoilerConfiguration configuration;
    private final ModbusService modbusService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TemperatureSensorsService temperatureSensorsService;
    private final HeatRequestService heatRequestService;
    private final HistoryService historyService;
    private GasBoilerStatus status = GasBoilerStatus.INIT;
    private Float lastDirectTemperature = null;
    private Float maxDirectTemperatureForPeriod = null;

    public GasBoilerServiceImpl(GasBoilerConfiguration configuration,
                                ModbusService modbusService,
                                ApplicationEventPublisher applicationEventPublisher,
                                TemperatureSensorsService temperatureSensorsService,
                                HeatRequestService heatRequestService,
                                HistoryService historyService,
                                MeterRegistry meterRegistry) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.temperatureSensorsService = temperatureSensorsService;
        this.heatRequestService = heatRequestService;
        this.historyService = historyService;

        Gauge.builder("gas_boiler", this::getNumericStatus)
                .tag("component", "status")
                .tag("system", "home_automation")
                .description("Статус газового котла")
                .register(meterRegistry);

        Gauge.builder("gas_boiler", this::getTemperatureDeltaIfWorks)
                .tag("component", "delta")
                .tag("system", "home_automation")
                .description("Дельта подачи/обратки при работе")
                .register(meterRegistry);

        Gauge.builder("gas_boiler", this::getGasBoilerRelayNumericStatus)
                .tag("component", "relay_status")
                .tag("system", "home_automation")
                .description("Статус реле газового котла")
                .register(meterRegistry);

        Gauge.builder("gas_boiler", this::calculateTargetDirectTemperature)
                .tag("component", "target_direct_temperature")
                .tag("system", "home_automation")
                .description("Расчетная температура подачи из газового котла по ПЗА")
                .register(meterRegistry);

        Gauge.builder("gas_boiler", this::calculateMinReturnTemperature)
                .tag("component", "min_return_temperature")
                .tag("system", "home_automation")
                .description("Расчетная температура обратки из газового котла при которой разрешено включение")
                .register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${gasBoiler.direct.pollInterval}")
    private void control() {
        logger.debug("Запущена задача управления газовым котлом");

        GasBoilerStatus newStatus = calculateStatus();

        if (status == GasBoilerStatus.WORKS && newStatus == GasBoilerStatus.IDLE) {
            Float targetDirectTemperature = calculateTargetDirectTemperature();
            if (targetDirectTemperature != null &&
                    maxDirectTemperatureForPeriod != null &&
                    maxDirectTemperatureForPeriod > targetDirectTemperature - configuration.getTemperatureDirectBlockDelta()) {
                logger.info("Газовый котел достиг целевой температуры в этом цикле, поэтому блокируем реле");
                turnOff();
            }
            logger.debug("Обнуляем максимальную температуру подачи");
            maxDirectTemperatureForPeriod = null;
        }
        else if (heatRequestService.getStatus() == HeatRequestStatus.NEED_HEAT ||
                heatRequestService.getStatus() == HeatRequestStatus.ERROR) {
            logger.debug("Запроса на тепло есть, разрешаем работу газового котла");
            turnOn();
        }
        else if (heatRequestService.getStatus() == HeatRequestStatus.NO_NEED_HEAT) {
            logger.debug("Запроса на тепло нет, запрещаем работу газового котла");
            turnOff();
        }

        status = newStatus;
    }

    private GasBoilerStatus calculateStatus() {
        Instant now = Instant.now();

        Float newDirectTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE);
        Float newReturnTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE);

        GasBoilerStatus status;

        if (getGasBoilerRelayStatus() == GasBoilerRelayStatus.NO_NEED_HEAT) {
            logger.debug("Реле отключено - статус котла считать не имеет смысла");
            status = GasBoilerStatus.IDLE;
        }
        else if (newDirectTemperature == null || newReturnTemperature == null) {
            logger.warn("Не удалось вычислить статус газового котла");
            status = GasBoilerStatus.ERROR;
            /* Можно сделать событие о невозможности рассчитать статус газового котла. Но зачем оно? */
        }
        else if (lastDirectTemperature == null) {
            logger.debug("Появилась температура подачи {}, но статус котла пока неизвестен", newDirectTemperature);
            status = GasBoilerStatus.INIT;
        }
        else
            /* считаем, что котел работает когда температура подачи растет либо не слишком сильно упала относительно
            максимума за период работы */
            /* и когда дельта между подачей и обраткой больше порога */
            if ((newDirectTemperature > lastDirectTemperature ||
                    ((maxDirectTemperatureForPeriod != null &&
                            newDirectTemperature >
                                    maxDirectTemperatureForPeriod - configuration.getTurnOffDirectDelta()))) &&
                    newDirectTemperature - newReturnTemperature > configuration.getTurnOnMinDelta()) {
                logger.info("Статус газового котла - работает");
                status = GasBoilerStatus.WORKS;
            }
            else {
                logger.info("Статус газового котла - не работает");
                status = GasBoilerStatus.IDLE;
            }

        if (newDirectTemperature != null) {
            logger.debug("Записываем новую температуру подачи {} вместо старой {}",
                    newDirectTemperature,
                    lastDirectTemperature);
            lastDirectTemperature = newDirectTemperature;
        }

        if (status == GasBoilerStatus.WORKS || status == GasBoilerStatus.INIT) {
            if (maxDirectTemperatureForPeriod == null || newDirectTemperature > maxDirectTemperatureForPeriod) {
                logger.debug("Записываем максимальную температуру подачи {} вместо старой {}",
                        newDirectTemperature,
                        maxDirectTemperatureForPeriod);
                maxDirectTemperatureForPeriod = newDirectTemperature;
            }

            logger.debug("Отправляем в историю текущую температуру подачи {}", newDirectTemperature);
            historyService.putTemperatureToDailyHistory(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE,
                    newDirectTemperature,
                    now);

            logger.debug("Отправляем в историю текущую температуру обратки {}", newReturnTemperature);
            historyService.putTemperatureToDailyHistory(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE,
                    newReturnTemperature,
                    now);
        }
        else {
            logger.debug("Цикл работы котла закончен");
        }

        logger.debug("Отправляем в историю текущий статус котла {}", status);
        historyService.putGasBoilerStatusToDailyHistory(status, now);

        return status;
    }

    private float calculateMinReturnTemperature() {
        /* рассчитываем температуру обратки в зависимости от температуры на улице (линейная функция) */
        /* в котле BAXI ПЗА устанавливается кривой, каждая кривая имеет свои границы, эти границы и будут
        координатами нашей прямой по оси X */
        Float outsideTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);

        if (outsideTemperature == null) {
            float targetReturnTemperature = configuration.getTemperatureReturnOnMinCurvePoint();
            logger.debug("Нет информации о температуре на улице, расчетная температура обратки для включения {} C°",
                    targetReturnTemperature);
            return targetReturnTemperature;
        }

        if (outsideTemperature <= configuration.getTemperatureWeatherCurveMin()) {
            float targetReturnTemperature = configuration.getTemperatureReturnOnMinCurvePoint();
            logger.debug("Температура на улице ниже минимальной температуры климатической кривой, расчетная " +
                            "температура обратки для включения {} C°", targetReturnTemperature);
            return targetReturnTemperature;
        }

        /* решаем задачу нахождения функции для прямой проходящей через 2 точки, по оси X температура на улице, по
        оси Y температура обратки */

        /* коэффициент наклона прямой */
        float m = (configuration.getTemperatureReturnOnMaxCurvePoint() - configuration.getTemperatureReturnOnMinCurvePoint()) /
                (configuration.getTemperatureWeatherCurveMax() - configuration.getTemperatureWeatherCurveMin());

        /* свободный член уравнения*/
        float b = configuration.getTemperatureReturnOnMinCurvePoint() - m * configuration.getTemperatureWeatherCurveMin();

        float targetReturnTemperature = m * outsideTemperature + b;
        if (targetReturnTemperature < configuration.getTemperatureReturnMin()) {
            targetReturnTemperature = configuration.getTemperatureReturnMin();
            logger.debug("Расчетная температура обратки для включения меньше минимальной, используем минимальную {} C°",
                    targetReturnTemperature);
            return targetReturnTemperature;
        }
        logger.debug("Расчетная температура обратки для включения {} C°", targetReturnTemperature);
        return targetReturnTemperature;
    }

    @Override
    @Nullable
    public Float calculateTargetDirectTemperature() {
        /* рассчитываем температуру подачи в зависимости от температуры на улице (линейная функция) */
        /* в котле BAXI ПЗА устанавливается кривой, каждая кривая имеет свои границы, эти границы и будут
        координатами нашей прямой по оси X */
        Float outsideTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);

        if (outsideTemperature == null) {
            logger.warn("Нет информации о температуре на улице, невозможно рассчитать температуру подачи");
            return null;
        }

        if (outsideTemperature <= configuration.getTemperatureWeatherCurveMin()) {
            float targetDirectTemperature = configuration.getTemperatureDirectMax();
            logger.debug("Температура на улице ниже минимальной температуры климатической кривой, расчетная " +
                    "температура " +
                    "подачи {} C°", targetDirectTemperature);
            return targetDirectTemperature;
        }

        if (outsideTemperature >= configuration.getTemperatureWeatherCurveMax()) {
            float targetDirectTemperature = configuration.getTemperatureDirectMin();
            logger.debug("Температура на улице выше максимальной температуры климатической кривой, расчетная " +
                    "температура " +
                    "подачи {} C°", targetDirectTemperature);
            return targetDirectTemperature;
        }

        /* решаем задачу нахождения функции для прямой проходящей через 2 точки, по оси X температура на улице, по
        оси Y температура обратки */

        /* коэффициент наклона прямой */
        float m = (configuration.getTemperatureDirectMin() - configuration.getTemperatureDirectMax()) /
                (configuration.getTemperatureWeatherCurveMax() - configuration.getTemperatureWeatherCurveMin());

        /* свободный член уравнения*/
        float b = configuration.getTemperatureDirectMax() - m * configuration.getTemperatureWeatherCurveMin();

        float targetDirectTemperature = m * outsideTemperature + b;
        logger.debug("Температура на улице в пределах климатической кривой, расчетная температура подачи {} C°",
                targetDirectTemperature);
        return targetDirectTemperature;
    }

    private void turnOn() {
        if (getGasBoilerRelayStatus() != GasBoilerRelayStatus.NEED_HEAT) {
            if (!ifGasBoilerCanBeTurnedOn()) {
                logger.debug("Газовый котел не может быть включен на отопление по политике тактования");
            }
            else
                try {
                    modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
                    logger.info("Включаем реле газового котла");
                } catch (ModbusException e) {
                    logger.error("Ошибка переключения статуса реле газового котла");
                    applicationEventPublisher.publishEvent(new GasBoilerErrorEvent(this));
                }
        } else {
            logger.debug("Реле газового котла уже включено");
        }
    }

    private boolean ifGasBoilerCanBeTurnedOn() {
        /* проверяем можно ли уже включать котел по температуре обратки */
        Float returnTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE);

        return returnTemperature == null || returnTemperature < calculateMinReturnTemperature();
    }

    private void turnOff() {
        if (getGasBoilerRelayStatus() != GasBoilerRelayStatus.NO_NEED_HEAT) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
                logger.info("Отключаем реле газового котла");
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле газового котла");
                applicationEventPublisher.publishEvent(new GasBoilerErrorEvent(this));
            }
        } else {
            logger.debug("Реле газового котла уже отключено");
        }
    }

    private GasBoilerRelayStatus getGasBoilerRelayStatus() {
        try {
            boolean[] pollResult = modbusService.readAllCoilsFromZero(configuration.getAddress());
            if (pollResult.length < 1) {
                throw new ModbusException("Опрос катушек реле газового котла вернул пустой массив");
            }
            if (pollResult[configuration.getCoil()]) {
                return GasBoilerRelayStatus.NO_NEED_HEAT;
            }
            else {
                return GasBoilerRelayStatus.NEED_HEAT;
            }

        } catch (ModbusException e) {
            logger.error("Ошибка получения статуса реле газового котла", e);
            applicationEventPublisher.publishEvent(new GasBoilerErrorEvent(this));
            return GasBoilerRelayStatus.ERROR;
        }
    }

    private int getGasBoilerRelayNumericStatus() {
        return getGasBoilerRelayStatus().getNumericStatus();
    }

    @Override
    public GasBoilerStatus getStatus() {
        return status;
    }

    private int getNumericStatus() {
        return status.getNumericStatus();
    }

    private Float getTemperatureDeltaIfWorks() {
        Float gasBoilerDirectTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE);
        Float gasBoilerReturnTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.WATER_RETURN_GAS_BOILER_TEMPERATURE);

        if (getStatus() != GasBoilerStatus.WORKS ||
                gasBoilerDirectTemperature == null ||
                gasBoilerReturnTemperature == null) {
            return null;
        }
        else {
            return gasBoilerDirectTemperature - gasBoilerReturnTemperature;
        }
    }

    @Override
    public String getFormattedStatus() {
        Float targetDirectTemperature = calculateTargetDirectTemperature();
        String formattedTargetDirectTemperature;
        if (targetDirectTemperature == null) {
            formattedTargetDirectTemperature=  "ошибка расчета!";
        } else {
            DecimalFormat df = new DecimalFormat("#.#");
            formattedTargetDirectTemperature = df.format(targetDirectTemperature) + " C°";
        }
        return status.getTemplate() +
                "\n* целевая подача из газового котла " + formattedTargetDirectTemperature;
    }
}

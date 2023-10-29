package home.automation.service.impl;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.BypassRelayStatus;
import home.automation.enums.FloorHeatingStatus;
import home.automation.enums.GasBoilerRelayStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.GasBoilerRelaySetFailEvent;
import home.automation.event.info.BypassRelayStatusCalculatedEvent;
import home.automation.event.info.FloorHeatingStatusCalculatedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.BypassRelayService;
import home.automation.service.FloorHeatingService;
import home.automation.service.GasBoilerService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GasBoilerServiceImpl implements GasBoilerService {
    private static final Logger logger = LoggerFactory.getLogger(GasBoilerServiceImpl.class);

    private final TemperatureSensor waterDirectGasBoilerTemperature =
        TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE;

    private final GasBoilerConfiguration configuration;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final TemperatureSensorsService temperatureSensorsService;

    private final BypassRelayService bypassRelayService;

    private final FloorHeatingService floorHeatingService;

    private final Map<Instant, GasBoilerStatus> gasBoilerStatusDailyHistory = new HashMap<>();

    private GasBoilerStatus calculatedStatus = GasBoilerStatus.INIT;

    private GasBoilerRelayStatus relayStatus = GasBoilerRelayStatus.INIT;

    private Float lastDirectTemperature;

    public GasBoilerServiceImpl(
        GasBoilerConfiguration configuration,
        ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher,
        TemperatureSensorsService temperatureSensorsService,
        BypassRelayService bypassRelayService,
        FloorHeatingService floorHeatingService
    ) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.temperatureSensorsService = temperatureSensorsService;
        this.bypassRelayService = bypassRelayService;
        this.floorHeatingService = floorHeatingService;
    }

    @EventListener
    public void onApplicationEvent(BypassRelayStatusCalculatedEvent event) {
        logger.debug("Получено событие о расчете статуса реле байпаса");
        switch (event.getStatus()) {
            case OPEN -> turnOn();
            case CLOSED -> {
                // проверяем, нужно ли теплым полам тепло, если нет - гасим котел
                if (floorHeatingService.getStatus() == FloorHeatingStatus.NO_NEED_HEAT) {
                    turnOff();
                }
            }
        }
    }

    @EventListener
    public void onApplicationEvent(FloorHeatingStatusCalculatedEvent event) {
        logger.debug("Получено событие о расчете запроса тепла в полы");
        switch (event.getStatus()) {
            case NEED_HEAT -> turnOn();
            case NO_NEED_HEAT -> {
                // проверяем, нужно ли радиаторам тепло, если нет - гасим котел
                if (bypassRelayService.getStatus() == BypassRelayStatus.CLOSED) {
                    turnOff();
                }
            }
        }
    }

    private void turnOn() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
            relayStatus = GasBoilerRelayStatus.NEED_HEAT;
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
            relayStatus = GasBoilerRelayStatus.ERROR;
        }
    }

    private void turnOff() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
            relayStatus = GasBoilerRelayStatus.NO_NEED_HEAT;
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
            relayStatus = GasBoilerRelayStatus.ERROR;
        }
    }

    @Scheduled(fixedRateString = "${gasBoiler.direct.pollInterval}")
    private void calculateStatus() {
        logger.debug("Запущена задача расчета статуса газового котла");
        if (GasBoilerRelayStatus.NO_NEED_HEAT == relayStatus) {
            logger.debug("Реле котла не замкнуто, значит газовый котел не работает");
            calculatedStatus = GasBoilerStatus.IDLE;
            return;
        }

        Float newDirectTemperature =
            temperatureSensorsService.getCurrentTemperatureForSensor(waterDirectGasBoilerTemperature);

        if (newDirectTemperature == null) {
            logger.warn("Не удалось вычислить статус газового котла");
            calculatedStatus = GasBoilerStatus.ERROR;
            lastDirectTemperature = null;
            /* Можно сделать событие о невозможности рассчитать статус газового котла. Но зачем оно? */
            return;
        }

        if (lastDirectTemperature == null) {
            logger.debug("Появилась температура, но статус котла пока неизвестен");
            calculatedStatus = GasBoilerStatus.ERROR;
            lastDirectTemperature = newDirectTemperature;
            return;
        }

        if (newDirectTemperature >= lastDirectTemperature) {
            logger.debug("Котел работает");
            calculatedStatus = GasBoilerStatus.WORKS;
        } else {
            logger.debug("Котел не работает");
            calculatedStatus = GasBoilerStatus.IDLE;
        }
        lastDirectTemperature = newDirectTemperature;
        putGasBoilerStatusToDailyHistory(calculatedStatus);
    }

    private void putGasBoilerStatusToDailyHistory(GasBoilerStatus calculatedStatus) {
        gasBoilerStatusDailyHistory.put(Instant.now(), calculatedStatus);
        gasBoilerStatusDailyHistory.entrySet()
            .removeIf(entry -> entry.getKey().isBefore(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    @Override
    public GasBoilerStatus getStatus() {
        return calculatedStatus;
    }

    @Override
    public String getFormattedStatus() {
        return calculatedStatus.getTemplate();
    }

    @Override
    public String getFormattedStatusForLastDay() {
        /* считаем, что можно рассчитать статистику имея данные за 23 часа работы котла */
        if (gasBoilerStatusDailyHistory.isEmpty() || Collections.min(gasBoilerStatusDailyHistory.keySet())
            .isAfter(Instant.now().minus(23, ChronoUnit.HOURS))) {
            return "сведений о работе котла пока не достаточно";
        }
        long countWorks = gasBoilerStatusDailyHistory.values().stream().filter(GasBoilerStatus.WORKS::equals).count();
        long countIdle = gasBoilerStatusDailyHistory.values().stream().filter(GasBoilerStatus.IDLE::equals).count();

        DecimalFormat df = new DecimalFormat("#");
        float percent = (float) countWorks / (countWorks + countIdle);
        return "за последние сутки котел работал на отопление " + df.format(percent) + "% времени";
    }
}

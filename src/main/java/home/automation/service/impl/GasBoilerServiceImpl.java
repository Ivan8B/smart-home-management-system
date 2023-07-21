package home.automation.service.impl;

import java.text.DecimalFormat;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.GasBoilerRelayStatus;
import home.automation.enums.GasBoilerStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.BypassRelayStatusCalculatedEvent;
import home.automation.event.GasBoilerRelaySetFailEvent;
import home.automation.exception.ModbusException;
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

    private GasBoilerStatus calculatedStatus = GasBoilerStatus.INIT;

    private final TemperatureSensor gasBoilerDirectSensor = TemperatureSensor.WATER_DIRECT_GAS_BOILER_TEMPERATURE;

    private Float lastDirectTemperature;

    private final GasBoilerConfiguration configuration;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final TemperatureSensorsService temperatureSensorsService;

    public GasBoilerServiceImpl(
        GasBoilerConfiguration configuration,
        ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher,
        TemperatureSensorsService temperatureSensorsService
    ) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.temperatureSensorsService = temperatureSensorsService;
    }

    @EventListener
    public void onApplicationEvent(BypassRelayStatusCalculatedEvent event) {
        logger.debug("Получено событие о расчете статуса реле байпаса");
        switch (event.getStatus()) {
            case OPEN -> turnOn();
            case CLOSED -> turnOff();
        }
    }

    @Override
    public String manualTurnOn() {
        logger.info("Ручное включение котла");
        turnOn();
        return getFormattedStatus();
    }

    @Override
    public String manualTurnOff() {
        logger.info("Ручное отключение котла");
        turnOff();
        return getFormattedStatus();
    }

    private void turnOn() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
        }
    }

    private void turnOff() {
        try {
            modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
        } catch (ModbusException e) {
            logger.error("Ошибка переключения статуса реле");
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
        }
    }

    @Scheduled(fixedRateString = "${gasBoiler.direct.pollInterval}")
    private void calculateStatus() {
        logger.debug("Запущена задача расчета статуса газового котла");
        if (GasBoilerRelayStatus.NO_NEED_HEAT == getRelayStatus()) {
            logger.debug("Запроса на тепло нет, значит газовый котел не работает");
            calculatedStatus = GasBoilerStatus.IDLE;
            return;
        }

        Float newDirectTemperature = temperatureSensorsService.getCurrentTemperatureForSensor(gasBoilerDirectSensor);

        if (newDirectTemperature == null) {
            logger.warn("Не удалось вычислить статус газового котла");
            calculatedStatus = GasBoilerStatus.ERROR;
            lastDirectTemperature = null;
            return;
        }

        if (lastDirectTemperature == null) {
            logger.debug("Появилась температура, но статус котла пока неизвестен");
            calculatedStatus = GasBoilerStatus.ERROR;
            lastDirectTemperature = newDirectTemperature;
            return;
        }

        if (newDirectTemperature > lastDirectTemperature) {
            logger.debug("Котел работает");
            calculatedStatus = GasBoilerStatus.WORKS;
            lastDirectTemperature = newDirectTemperature;
        } else {
            logger.debug("Котел не работает");
            calculatedStatus = GasBoilerStatus.WORKS;
            lastDirectTemperature = newDirectTemperature;
        }
    }

    @Override
    public GasBoilerStatus getStatus() {
        return calculatedStatus;
    }

    public GasBoilerRelayStatus getRelayStatus() {
        try {
            boolean[] pollResult = modbusService.readAllCoilsFromZero(configuration.getAddress());
            if (pollResult.length < 1) {
                throw new ModbusException("Опрос катушек вернул пустой массив");
            }
            if (pollResult[configuration.getCoil()]) {
                return GasBoilerRelayStatus.NEED_HEAT;
            } else {
                return GasBoilerRelayStatus.NO_NEED_HEAT;
            }

        } catch (ModbusException e) {
            logger.error("Ошибка получения статуса реле газового котла", e);
            applicationEventPublisher.publishEvent(new GasBoilerRelaySetFailEvent(this));
            return GasBoilerRelayStatus.ERROR;
        }
    }

    @Override
    public String getFormattedStatus() {
        return getStatus().getTemplate() + "\n" + gasBoilerDirectSensor.getTemplate() + " " + formatTemperature(
            lastDirectTemperature);
    }

    private String formatTemperature(Float temperature) {
        if (temperature == null) {
            return "";
        }
        DecimalFormat df = new DecimalFormat("#.#");
        return df.format(temperature) + " C°";
    }
}

package home.automation.service.impl;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.enums.GasBoilerRelayStatus;
import home.automation.event.BypassRelayStatusCalculatedEvent;
import home.automation.event.GasBoilerRelaySetFailEvent;
import home.automation.exception.ModbusException;
import home.automation.service.GasBoilerService;
import home.automation.service.ModbusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class GasBoilerServiceImpl implements GasBoilerService {
    private static final Logger logger = LoggerFactory.getLogger(GasBoilerServiceImpl.class);

    private final GasBoilerConfiguration configuration;

    private final ModbusService modbusService;

    private final ApplicationEventPublisher applicationEventPublisher;

    public GasBoilerServiceImpl(
        GasBoilerConfiguration configuration, ModbusService modbusService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.applicationEventPublisher = applicationEventPublisher;
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

    @Override
    public GasBoilerRelayStatus getStatus() {
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
        return getStatus().getTemplate();
        //TODO может еще температуру подачи?
    }
}

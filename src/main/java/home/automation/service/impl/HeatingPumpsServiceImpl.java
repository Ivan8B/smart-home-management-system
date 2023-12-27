package home.automation.service.impl;

import home.automation.configuration.HeatingPumpsRelayConfiguration;
import home.automation.enums.HeatingPumpsStatus;
import home.automation.event.error.HeatingPumpsErrorEvent;
import home.automation.event.info.HeatRequestCalculatedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.HeatingPumpsService;
import home.automation.service.ModbusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class HeatingPumpsServiceImpl implements HeatingPumpsService {
    private final Logger logger = LoggerFactory.getLogger(HeatingPumpsServiceImpl.class);

    private final HeatingPumpsRelayConfiguration configuration;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ModbusService modbusService;

    public HeatingPumpsServiceImpl(
        HeatingPumpsRelayConfiguration configuration,
        ApplicationEventPublisher applicationEventPublisher,
        ModbusService modbusService
    ) {
        this.configuration = configuration;
        this.applicationEventPublisher = applicationEventPublisher;
        this.modbusService = modbusService;
    }

    @EventListener
    public void onApplicationEvent(HeatRequestCalculatedEvent event) {
        logger.debug("Получено событие о расчете статуса запроса на тепло");
        switch (event.getStatus()) {
            case NEED_HEAT -> {
                logger.info("Есть запрос на тепло в дом, включаем насосы");
                turnOn();
            }
            case NO_NEED_HEAT -> {
                logger.info("Нет запроса на тепло в дом, отключаем насосы");
                turnOff();
            }
            case ERROR -> {
                logger.info("Ошибка запроса тепло в дом, на всякий случай включаем насосы");
                turnOn();
            }
        }
    }

    private void turnOn() {
        if (getStatus() != HeatingPumpsStatus.TURNED_ON) {
            try {
                /* реле нормально закрытое, управление инвертировано */
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), false);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле насосов отопления");
                applicationEventPublisher.publishEvent(new HeatingPumpsErrorEvent(this));
            }
        }
    }

    private void turnOff() {
        if (getStatus() != HeatingPumpsStatus.TURNED_OFF) {
            try {
                modbusService.writeCoil(configuration.getAddress(), configuration.getCoil(), true);
            } catch (ModbusException e) {
                logger.error("Ошибка переключения статуса реле насосов отопления");
                applicationEventPublisher.publishEvent(new HeatingPumpsErrorEvent(this));
            }
        }
    }

    @Override
    public HeatingPumpsStatus getStatus() {
        try {
            boolean[] pollResult = modbusService.readAllCoilsFromZero(configuration.getAddress());
            if (pollResult.length < 1) {
                throw new ModbusException("Опрос катушек реле насосов отопления вернул пустой массив");
            }
            if (pollResult[configuration.getCoil()]) {
                return HeatingPumpsStatus.TURNED_OFF;
            } else {
                return HeatingPumpsStatus.TURNED_ON;
            }

        } catch (ModbusException e) {
            logger.error("Ошибка получения статуса реле насосов отопления", e);
            applicationEventPublisher.publishEvent(new HeatingPumpsErrorEvent(this));
            return HeatingPumpsStatus.ERROR;
        }
    }

    @Override
    public String getFormattedStatus() {
        return getStatus().getTemplate();
    }
}

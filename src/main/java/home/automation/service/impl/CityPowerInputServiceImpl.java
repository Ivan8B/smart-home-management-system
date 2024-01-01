package home.automation.service.impl;

import home.automation.configuration.CityPowerInputConfiguration;
import home.automation.enums.CityPowerInputStatus;
import home.automation.event.error.CityPowerInputErrorEvent;
import home.automation.event.info.CityPowerInputNoPowerEvent;
import home.automation.exception.ModbusException;
import home.automation.service.CityPowerInputService;
import home.automation.service.ModbusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CityPowerInputServiceImpl implements CityPowerInputService {
    private final Logger logger = LoggerFactory.getLogger(CityPowerInputServiceImpl.class);

    private final CityPowerInputConfiguration configuration;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ModbusService modbusService;

    public CityPowerInputServiceImpl(
        CityPowerInputConfiguration configuration,
        ApplicationEventPublisher applicationEventPublisher,
        ModbusService modbusService
    ) {
        this.configuration = configuration;
        this.applicationEventPublisher = applicationEventPublisher;
        this.modbusService = modbusService;
    }

    @Scheduled(fixedRateString = "${cityPowerInput.controlInterval}")
    private void control() {
        logger.debug("Запущена задача опроса наличия напряжения на входе ИБП");
        getStatus();
    }

    @Override
    public CityPowerInputStatus getStatus() {
        try {
            boolean[] pollResult = modbusService.readAllDiscreteInputsFromZero(configuration.getAddress());

            if (pollResult.length < 1) {
                throw new ModbusException("Опрос реле напряжения на входе ИБП вернул пустой массив");
            }

            if (pollResult[configuration.getDiscreteInput()]) {
                logger.debug("Статус напряжения на входе ИБП - есть");
                return CityPowerInputStatus.POWER_ON;
            } else {
                logger.debug("Статус напряжения на входе ИБП - нет");
                logger.debug("Отправляем событие об отсутствии питания");
                applicationEventPublisher.publishEvent(new CityPowerInputNoPowerEvent(this));
                return CityPowerInputStatus.POWER_OFF;
            }
        } catch (ModbusException e) {
            logger.error("Ошибка опроса реле напряжения на входе ИБП", e);
            logger.debug("Отправляем событие об ошибке поллинга реле напряжения на входе ИБП");
            applicationEventPublisher.publishEvent(new CityPowerInputErrorEvent(this));
            return CityPowerInputStatus.ERROR;
        }
    }

    @Override
    public String getFormattedStatus() {
        return getStatus().getTemplate();
    }
}

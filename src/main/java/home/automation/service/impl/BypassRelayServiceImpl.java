package home.automation.service.impl;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import home.automation.configuration.BypassRelayConfiguration;
import home.automation.enums.BypassRelayStatus;
import home.automation.event.BypassRelayPollErrorEvent;
import home.automation.event.BypassRelayStatusCalculatedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.BypassRelayService;
import home.automation.service.ModbusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BypassRelayServiceImpl implements BypassRelayService {
    private static final Logger logger = LoggerFactory.getLogger(BypassRelayServiceImpl.class);

    private BypassRelayStatus calculatedStatus = BypassRelayStatus.INIT;

    private final Map<Instant, BypassRelayStatus> pollingResults = new ConcurrentHashMap<>();

    private final BypassRelayConfiguration bypassRelayConfiguration;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ModbusService modbusService;

    public BypassRelayServiceImpl(
        BypassRelayConfiguration bypassRelayConfiguration,
        ApplicationEventPublisher applicationEventPublisher,
        ModbusService modbusService
    ) {
        this.bypassRelayConfiguration = bypassRelayConfiguration;
        this.applicationEventPublisher = applicationEventPublisher;
        this.modbusService = modbusService;
    }

    @Override
    public BypassRelayStatus getBypassRelayCalculatedStatus() {
        return calculatedStatus;
    }

    @Scheduled(fixedRateString = "${bypassRelay.pollInterval}")
    private void pollBypassRelay() {
        logger.debug("Запущена задача опроса реле байпаса");
        try {
            BypassRelayStatus currentPollResult;
            boolean[] pollResult = modbusService.readAllDiscreteInputsFromZero(bypassRelayConfiguration.getAddress());

            if (pollResult.length < 1) {
                throw new ModbusException("Опрос реле байпаса вернул пустой массив");
            }

            if (pollResult[bypassRelayConfiguration.getDiscreteInput()]) {
                currentPollResult = BypassRelayStatus.CLOSED;
                logger.debug("Статус реле байпаса - замкнуто");
            } else {
                currentPollResult = BypassRelayStatus.OPEN;
                logger.debug("Статус реле байпаса - разомкнуто");
            }

            logger.debug("Запущен расчет статуса реле");
            calculateStatus(Instant.now(), currentPollResult);

        } catch (ModbusException e) {
            logger.error("Ошибка опроса реле байпаса", e);
            logger.debug("Отправляем событие об ошибке поллинга реле байпаса");
            calculatedStatus = BypassRelayStatus.ERROR;
            publishPollErrorEvent();
        }
    }

    private void calculateStatus(Instant timestamp, BypassRelayStatus currentPollResult) {
        if (pollingResults.size() >= bypassRelayConfiguration.getPollCountInPeriod()) {
            int open_count = 0;
            for (BypassRelayStatus result : pollingResults.values()) {
                if (result == BypassRelayStatus.OPEN) {
                    open_count++;
                }
            }

            logger.info(
                "За период было {} опросов, из них в открытом состоянии реле байпаса было {} раз при пороге {} процентов",
                pollingResults.size(),
                open_count,
                bypassRelayConfiguration.getOpenThreshold()
            );

            if (open_count / pollingResults.size() * 100 >= bypassRelayConfiguration.getOpenThreshold()) {
                calculatedStatus = BypassRelayStatus.OPEN;
            } else {
                calculatedStatus = BypassRelayStatus.CLOSED;
            }
            logger.info(calculatedStatus.getTemplate());

            publishCalculatedEvent(getBypassRelayCalculatedStatus());
            logger.debug("Отправлено событие о новом статусе реле байпаса");

            pollingResults.clear();
        } else {
            pollingResults.put(timestamp, currentPollResult);
            logger.debug("Расчет завершен, в пуле пока мало данных");
        }
    }

    private void publishCalculatedEvent(BypassRelayStatus status) {
        BypassRelayStatusCalculatedEvent event = new BypassRelayStatusCalculatedEvent(this, status);
        applicationEventPublisher.publishEvent(event);
    }

    private void publishPollErrorEvent() {
        applicationEventPublisher.publishEvent(new BypassRelayPollErrorEvent(this));
    }
}
package home.automation.service.impl;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import home.automation.configuration.BypassRelayConfiguration;
import home.automation.enums.BypassRelayStatus;
import home.automation.event.error.BypassRelayPollErrorEvent;
import home.automation.event.info.BypassRelayStatusCalculatedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.BypassRelayService;
import home.automation.service.ModbusService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BypassRelayServiceImpl implements BypassRelayService {
    private static final Logger logger = LoggerFactory.getLogger(BypassRelayServiceImpl.class);
    private final Map<Instant, BypassRelayStatus> pollingResults = new ConcurrentHashMap<>();
    private final BypassRelayConfiguration bypassRelayConfiguration;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ModbusService modbusService;
    private BypassRelayStatus calculatedStatus = BypassRelayStatus.INIT;

    public BypassRelayServiceImpl(
        BypassRelayConfiguration bypassRelayConfiguration,
        ApplicationEventPublisher applicationEventPublisher,
        ModbusService modbusService,
        MeterRegistry meterRegistry
    ) {
        this.bypassRelayConfiguration = bypassRelayConfiguration;
        this.applicationEventPublisher = applicationEventPublisher;
        this.modbusService = modbusService;

        Gauge.builder("bypass_relay", this::getRawNumericStatus)
            .tag("component", "status_raw")
            .tag("system", "home_automation")
            .description("Статус реле байпаса (текущий)")
            .register(meterRegistry);

        Gauge.builder("bypass_relay", this::getCalculatedNumericStatus)
            .tag("component", "status_calculated")
            .tag("system", "home_automation")
            .description("Статус реле байпаса (рассчитанный)")
            .register(meterRegistry);
    }

    @Override
    public BypassRelayStatus getStatus() {
        return calculatedStatus;
    }

    @Override
    public String getFormattedStatus() {
        return calculatedStatus.getTemplate();
    }

    private Number getCalculatedNumericStatus() {
        return calculatedStatus.getNumericStatus();
    }

    private Number getRawNumericStatus() {
        return calculatedStatus.getNumericStatus();
    }

    @Scheduled(fixedRateString = "${bypass.pollInterval}")
    private void calculateBypassRelayStatus() {
        logger.debug("Запущена задача опроса реле байпаса");
        BypassRelayStatus currentPollResult = pollBypassRelay();

        if (currentPollResult == BypassRelayStatus.ERROR) {
            logger.debug("Не удается расчитать статус реле - ошибка опроса");
            calculatedStatus = BypassRelayStatus.ERROR;
            return;
        }

        logger.debug("Запущен расчет статуса реле");
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

            publishCalculatedEvent(getStatus());
            logger.debug("Отправлено событие о новом статусе реле байпаса");

            pollingResults.clear();
        } else {
            pollingResults.put(Instant.now(), currentPollResult);
            logger.debug("Расчет завершен, в пуле пока мало данных");
        }
    }

    private BypassRelayStatus pollBypassRelay() {
        try {
            boolean[] pollResult = modbusService.readAllDiscreteInputsFromZero(bypassRelayConfiguration.getAddress());

            if (pollResult.length < 1) {
                throw new ModbusException("Опрос реле байпаса вернул пустой массив");
            }

            if (pollResult[bypassRelayConfiguration.getDiscreteInput()]) {
                logger.debug("Статус реле байпаса - замкнуто");
                return BypassRelayStatus.CLOSED;
            } else {
                logger.debug("Статус реле байпаса - разомкнуто");
                return BypassRelayStatus.OPEN;
            }
        } catch (ModbusException e) {
            logger.error("Ошибка опроса реле байпаса", e);
            logger.debug("Отправляем событие об ошибке поллинга реле байпаса");
            publishPollErrorEvent();
            return BypassRelayStatus.ERROR;
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

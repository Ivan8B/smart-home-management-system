package home.heating.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import home.heating.enums.SelfMonitoringStatus;
import home.heating.enums.TemperatureSensor;
import home.heating.event.BypassRelayPollErrorEvent;
import home.heating.event.GasBoilerRelaySetFailEvent;
import home.heating.event.MinimalTemperatureLowEvent;
import home.heating.event.TemperatureSensorPollErrorEvent;
import home.heating.service.BotService;
import home.heating.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HealthServiceImpl implements HealthService {
    private static final Logger logger = LoggerFactory.getLogger(HealthServiceImpl.class);

    private SelfMonitoringStatus status = SelfMonitoringStatus.OK;

    private final BotService botService;

    private final List<BypassRelayPollErrorEvent> bypassRelayPollErrorEvents = new ArrayList<>();

    private final List<GasBoilerRelaySetFailEvent> gasBoilerRelaySetFailEvents = new ArrayList<>();

    private final Set<TemperatureSensor> criticalTemperatureSensorFailEvents = new HashSet<>();

    private final Set<TemperatureSensor> minorTemperatureSensorFailEvents = new HashSet<>();

    private final Set<TemperatureSensor> minimalTemperatureLowEvents = new HashSet<>();

    public HealthServiceImpl(
        BotService botService
    ) {
        this.botService = botService;
    }

    @Scheduled(fixedRateString = "${health.events.pollInterval}")
    private void checkHealth() {
        logger.debug("Запущена задача селфмониторинга");
        SelfMonitoringStatus newStatus = calculateStatus();
        if (!status.equals(newStatus)) {
            if (SelfMonitoringStatus.EMERGENCY.equals(newStatus)) {
                botService.notify(formatCriticalMessage());
            }
            if (SelfMonitoringStatus.MINOR_PROBLEMS.equals(newStatus)) {
                botService.notify(formatMinorMessage());
            }
            if (SelfMonitoringStatus.OK.equals(newStatus)) {
                botService.notify(formatOkMessage());
            }
            status = newStatus;
        }
        clear();
    }

    @EventListener
    public void onBypassRelayPollErrorEvent(BypassRelayPollErrorEvent event) {
        bypassRelayPollErrorEvents.add(event);
    }

    @EventListener
    public void onTemperatureSensorStatusChangedEvent(TemperatureSensorPollErrorEvent event) {
        if ((event.getSensor().isCritical())) {
            criticalTemperatureSensorFailEvents.add(event.getSensor());
        } else {
            minorTemperatureSensorFailEvents.add(event.getSensor());
        }
    }

    @EventListener
    public void onGasBoilerRelaySetFailEvent(GasBoilerRelaySetFailEvent event) {
        gasBoilerRelaySetFailEvents.add(event);
    }

    @EventListener
    public void onMinimalTemperatureLowEvent(MinimalTemperatureLowEvent event) {
        minimalTemperatureLowEvents.add(event.getSensor());
    }

    @Override
    public String getFormattedStatus() {
        return status.getTemplate();
    }

    private SelfMonitoringStatus calculateStatus() {
        if (!bypassRelayIsOk() || !criticalTemperatureSensorsAreOk() || !minimalTemperaturesAreOk()) {
            return SelfMonitoringStatus.EMERGENCY;
        }
        if (!minorTemperatureSensorsAreOk()) {
            return SelfMonitoringStatus.MINOR_PROBLEMS;
        }
        return SelfMonitoringStatus.OK;
    }

    private boolean bypassRelayIsOk() {
        return bypassRelayPollErrorEvents.isEmpty();
    }

    private boolean gasBoilerRelayIsOk() {
        return gasBoilerRelaySetFailEvents.isEmpty();
    }

    private boolean criticalTemperatureSensorsAreOk() {
        return criticalTemperatureSensorFailEvents.isEmpty();
    }

    private boolean minorTemperatureSensorsAreOk() {
        return minorTemperatureSensorFailEvents.isEmpty();
    }

    private boolean minimalTemperaturesAreOk() {
        return minimalTemperatureLowEvents.isEmpty();
    }

    private void clear() {
        bypassRelayPollErrorEvents.clear();
        gasBoilerRelaySetFailEvents.clear();
        minorTemperatureSensorFailEvents.clear();
        criticalTemperatureSensorFailEvents.clear();
        minimalTemperatureLowEvents.clear();
    }

    private String formatCriticalMessage() {
        StringBuilder message = new StringBuilder("Аварийная ситуация:\n");
        if (!bypassRelayIsOk()) {
            message.append("* отказ реле байпаса \n");
        }
        if (!gasBoilerRelayIsOk()) {
            message.append("* отказ реле газового котла \n");
        }
        if (!criticalTemperatureSensorFailEvents.isEmpty()) {
            message.append("* отказ критичных температурных датчиков: ");
            message.append(criticalTemperatureSensorFailEvents.stream().map(TemperatureSensor::getTemplate)
                .collect(Collectors.joining(", ")));
        }
        if (!minimalTemperaturesAreOk()) {
            message.append("* слишком низкая температура датчиков: ");
            message.append(minimalTemperatureLowEvents.stream().map(TemperatureSensor::getTemplate)
                .collect(Collectors.joining(", ")));
        }
        return message.toString();
    }

    private String formatMinorMessage() {
        return "Отказ второстепенных температурных датчиков:\n *" + minorTemperatureSensorFailEvents.stream()
            .map(TemperatureSensor::getTemplate).collect(Collectors.joining(", "));
    }

    private String formatOkMessage() {
        return "Ситуация нормализована";
    }
}

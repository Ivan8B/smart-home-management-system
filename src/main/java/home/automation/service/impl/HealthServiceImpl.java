package home.automation.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import home.automation.enums.SelfMonitoringStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.BypassRelayPollErrorEvent;
import home.automation.event.FloorHeatingStatusCalculateErrorEvent;
import home.automation.event.GasBoilerRelaySetFailEvent;
import home.automation.event.MinimalTemperatureLowEvent;
import home.automation.event.TemperatureSensorPollErrorEvent;
import home.automation.service.BotService;
import home.automation.service.HealthService;
import home.automation.service.TemperatureSensorsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HealthServiceImpl implements HealthService {
    private static final Logger logger = LoggerFactory.getLogger(HealthServiceImpl.class);

    private SelfMonitoringStatus status = SelfMonitoringStatus.OK;

    private final BotService botService;

    private final TemperatureSensorsService temperatureSensorsService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final List<BypassRelayPollErrorEvent> bypassRelayPollErrorEvents = new ArrayList<>();

    private final List<GasBoilerRelaySetFailEvent> gasBoilerRelaySetFailEvents = new ArrayList<>();

    private final List<FloorHeatingStatusCalculateErrorEvent> floorHeatingStatusCalculateErrorEvents =
        new ArrayList<>();

    private final Set<TemperatureSensor> criticalTemperatureSensorFailEvents = new HashSet<>();

    private final Set<TemperatureSensor> minorTemperatureSensorFailEvents = new HashSet<>();

    private final Set<TemperatureSensor> minimalTemperatureLowEvents = new HashSet<>();

    public HealthServiceImpl(
        BotService botService,
        TemperatureSensorsService temperatureSensorsService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.botService = botService;
        this.temperatureSensorsService = temperatureSensorsService;
        this.applicationEventPublisher = applicationEventPublisher;
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

    @Scheduled(fixedRateString = "${health.minimalTemperature.pollInterval}")
    private void checkMinimalTemperature() {
        Arrays.stream(TemperatureSensor.values())
            .filter(sensor -> sensor.isCritical() && sensor.getMinimalTemperature() != null).forEach(sensor -> {
                Float currentTemperatureForSensor = temperatureSensorsService.getCurrentTemperatureForSensor(sensor);
                if (currentTemperatureForSensor != null && currentTemperatureForSensor < sensor.getMinimalTemperature()) {
                    logger.warn(sensor.getTemplate() + " - слишком низкая температура!");
                    logger.debug("Отправляем событие о низкой температуре");
                    applicationEventPublisher.publishEvent(new MinimalTemperatureLowEvent(this, sensor));
                }
            });
    }

    @EventListener
    public void onBypassRelayPollErrorEvent(BypassRelayPollErrorEvent event) {
        bypassRelayPollErrorEvents.add(event);
    }

    @EventListener
    public void onFloorHeatingStatusCalculateErrorEvent(FloorHeatingStatusCalculateErrorEvent event) {
        floorHeatingStatusCalculateErrorEvents.add(event);
    }

    @EventListener
    public void onTemperatureSensorPollErrorEvent(TemperatureSensorPollErrorEvent event) {
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

    private boolean floorHeatingCalculationIsOk() {
        return floorHeatingStatusCalculateErrorEvents.isEmpty();
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
        floorHeatingStatusCalculateErrorEvents.clear();
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
        if (!floorHeatingCalculationIsOk()) {
            message.append("* не удалось рассчитать запрос на тепло в полы");
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
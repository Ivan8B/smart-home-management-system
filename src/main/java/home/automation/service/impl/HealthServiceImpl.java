package home.automation.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import home.automation.enums.CityPowerInputStatus;
import home.automation.enums.ElectricBoilerStatus;
import home.automation.enums.SelfMonitoringStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.CityPowerInputErrorEvent;
import home.automation.event.error.ElectricBoilerErrorEvent;
import home.automation.event.error.FloorHeatingErrorEvent;
import home.automation.event.error.FunnelHeatingErrorEvent;
import home.automation.event.error.GasBoilerErrorEvent;
import home.automation.event.error.HeatRequestErrorEvent;
import home.automation.event.error.StreetLightErrorEvent;
import home.automation.event.error.TemperatureSensorPollErrorEvent;
import home.automation.event.info.CityPowerInputNoPowerEvent;
import home.automation.event.info.ElectricBoilerTurnedOnEvent;
import home.automation.event.info.MinimalTemperatureLowEvent;
import home.automation.service.BotService;
import home.automation.service.CityPowerInputService;
import home.automation.service.ElectricBoilerService;
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
    private final BotService botService;
    private final TemperatureSensorsService temperatureSensorsService;
    private final ElectricBoilerService electricBoilerService;
    private final CityPowerInputService cityPowerInputService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final List<HeatRequestErrorEvent> heatRequestErrorEvents = new ArrayList<>();
    private final List<GasBoilerErrorEvent> gasBoilerErrorEvents = new ArrayList<>();
    private final List<ElectricBoilerErrorEvent> electricBoilerErrorEvents = new ArrayList<>();
    private final List<ElectricBoilerTurnedOnEvent> electricBoilerTurnedOnEvents = new ArrayList<>();
    private final List<CityPowerInputErrorEvent> cityPowerInputErrorEvents = new ArrayList<>();
    private final List<CityPowerInputNoPowerEvent> cityPowerInputNoPowerEvents = new ArrayList<>();
    private final List<FloorHeatingErrorEvent> floorHeatingErrorEvents = new ArrayList<>();
    private final List<StreetLightErrorEvent> streetLightErrorEvents = new ArrayList<>();
    private final List<FunnelHeatingErrorEvent> funnelHeatingErrorEvents = new ArrayList<>();
    private final Set<TemperatureSensor> criticalTemperatureSensorFailEvents = new HashSet<>();
    private final Set<TemperatureSensor> minorTemperatureSensorFailEvents = new HashSet<>();
    private final Set<TemperatureSensor> minimalTemperatureLowEvents = new HashSet<>();
    private SelfMonitoringStatus lastStatus = SelfMonitoringStatus.OK;

    public HealthServiceImpl(
        BotService botService,
        TemperatureSensorsService temperatureSensorsService,
        ElectricBoilerService electricBoilerService,
        CityPowerInputService cityPowerInputService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.botService = botService;
        this.temperatureSensorsService = temperatureSensorsService;
        this.electricBoilerService = electricBoilerService;
        this.cityPowerInputService = cityPowerInputService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedRateString = "${health.controlInterval}")
    private void control() {
        calculateHealthStatus();
    }

    private SelfMonitoringStatus calculateHealthStatus() {
        logger.debug("Запущена задача селфмониторинга");
        checkMinimalTemperature();

        SelfMonitoringStatus newStatus = SelfMonitoringStatus.OK;

        if (!heatRequestIsOk() || !gasBoilerIsOk() || !electricBoilerIsOk() || !electricBoilerIsTurnedOff()
            || !cityPowerInputIsOk() || !cityPowerInputHasPower() || !floorHeatingIsOk()
            || !criticalTemperatureSensorsAreOk() || !minimalTemperaturesAreOk()) {
            newStatus = SelfMonitoringStatus.EMERGENCY;
        }
        if (!minorTemperatureSensorsAreOk() || !streetLightRelayIsOk() || !funnelHeatingIsOk()) {
            newStatus = SelfMonitoringStatus.MINOR_PROBLEMS;
        }

        if (newStatus != lastStatus) {
            if (newStatus == SelfMonitoringStatus.EMERGENCY) {
                botService.notify(formatCriticalMessage());
            }
            if (newStatus == SelfMonitoringStatus.MINOR_PROBLEMS) {
                botService.notify(formatMinorMessage());
            }
            if (newStatus == SelfMonitoringStatus.OK) {
                botService.notify(formatOkMessage());
            }
            lastStatus = newStatus;
        }
        clear();
        return newStatus;
    }

    private void checkMinimalTemperature() {
        Arrays.stream(TemperatureSensor.values())
            .filter(sensor -> sensor.isCritical() && sensor.getMinimalTemperature() != null).forEach(sensor -> {
                Float currentTemperatureForSensor = temperatureSensorsService.getCurrentTemperatureForSensor(sensor);
                if (currentTemperatureForSensor != null && currentTemperatureForSensor < sensor.getMinimalTemperature()) {
                    logger.warn(
                        sensor.getTemplate() + " - слишком низкая температура - " + currentTemperatureForSensor + " C°!");
                    logger.debug("Отправляем событие о низкой температуре");
                    applicationEventPublisher.publishEvent(new MinimalTemperatureLowEvent(this, sensor));
                }
            });
    }

    @EventListener
    public void onHeatRequestErrorEvent(HeatRequestErrorEvent event) {
        heatRequestErrorEvents.add(event);
    }

    @EventListener
    public void onGasBoilerRelaySetFailEvent(GasBoilerErrorEvent event) {
        gasBoilerErrorEvents.add(event);
    }

    @EventListener
    public void onElectricBoilerErrorEvent(ElectricBoilerErrorEvent event) {
        electricBoilerErrorEvents.add(event);
    }

    @EventListener
    public void onElectricBoilerTurnedOnEvent(ElectricBoilerTurnedOnEvent event) {
        electricBoilerTurnedOnEvents.add(event);
    }

    @EventListener
    public void onCityPowerInputErrorEvent(CityPowerInputErrorEvent event) {
        cityPowerInputErrorEvents.add(event);
    }

    @EventListener
    public void onCityPowerInputNoPowerEvent(CityPowerInputNoPowerEvent event) {
        cityPowerInputNoPowerEvents.add(event);
    }

    @EventListener
    public void onFloorHeatingErrorEvent(FloorHeatingErrorEvent event) {
        floorHeatingErrorEvents.add(event);
    }

    @EventListener
    public void onStreetLightErrorEvent(StreetLightErrorEvent event) {
        streetLightErrorEvents.add(event);
    }

    @EventListener
    public void onFunnelHeatingErrorEvent(FunnelHeatingErrorEvent event) {
        funnelHeatingErrorEvents.add(event);
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
    public void onMinimalTemperatureLowEvent(MinimalTemperatureLowEvent event) {
        minimalTemperatureLowEvents.add(event.getSensor());
    }

    @Override
    public String getFormattedStatus() {
        return calculateHealthStatus().getTemplate();
    }

    private boolean heatRequestIsOk() {
        return heatRequestErrorEvents.isEmpty();
    }

    private boolean gasBoilerIsOk() {
        return gasBoilerErrorEvents.isEmpty();
    }

    private boolean electricBoilerIsOk() {
        return electricBoilerErrorEvents.isEmpty();
    }

    private boolean electricBoilerIsTurnedOff() {
        return electricBoilerTurnedOnEvents.isEmpty() && electricBoilerService.getStatus() == ElectricBoilerStatus.TURNED_OFF;
    }

    private boolean cityPowerInputIsOk() {
        return cityPowerInputErrorEvents.isEmpty();
    }

    private boolean cityPowerInputHasPower() {
        return cityPowerInputNoPowerEvents.isEmpty() && cityPowerInputService.getStatus() == CityPowerInputStatus.POWER_ON;
    }

    private boolean floorHeatingIsOk() {
        return floorHeatingErrorEvents.isEmpty();
    }

    private boolean streetLightRelayIsOk() {
        return streetLightErrorEvents.isEmpty();
    }

    private boolean funnelHeatingIsOk() {
        return funnelHeatingErrorEvents.isEmpty();
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
        heatRequestErrorEvents.clear();
        gasBoilerErrorEvents.clear();
        electricBoilerErrorEvents.clear();
        electricBoilerTurnedOnEvents.clear();
        cityPowerInputErrorEvents.clear();
        cityPowerInputNoPowerEvents.clear();
        floorHeatingErrorEvents.clear();
        streetLightErrorEvents.clear();
        funnelHeatingErrorEvents.clear();
        minorTemperatureSensorFailEvents.clear();
        criticalTemperatureSensorFailEvents.clear();
        minimalTemperatureLowEvents.clear();
    }

    private String formatCriticalMessage() {
        StringBuilder message = new StringBuilder("Аварийная ситуация:\n");
        if (!heatRequestIsOk()) {
            message.append("* отказ расчета необходимости отопления\n");
        }
        if (!gasBoilerIsOk()) {
            message.append("* отказ реле газового котла\n");
        }
        if (!electricBoilerIsOk()) {
            message.append("* отказ реле электрического котла\n");
        }
        if (!electricBoilerIsTurnedOff()) {
            message.append("* работает электрический котел\n");
        }
        if (!cityPowerInputIsOk()) {
            message.append("* отказ реле напряжения на входе ИБП\n");
        }
        if (!cityPowerInputHasPower()) {
            message.append("* нет напряжения на входе ИБП\n");
        }
        if (!floorHeatingIsOk()) {
            message.append("* отказ управления теплым полом\n");
        }
        if (!criticalTemperatureSensorsAreOk()) {
            message.append("* отказ критичных температурных датчиков: ");
            message.append(criticalTemperatureSensorFailEvents.stream().map(TemperatureSensor::getTemplate)
                .collect(Collectors.joining(", ")));
            message.append("\n");;
        }
        if (!minimalTemperaturesAreOk()) {
            message.append("* слишком низкая температура датчиков: ");
            message.append(minimalTemperatureLowEvents.stream().map(TemperatureSensor::getTemplate)
                .collect(Collectors.joining(", ")));
            message.append("\n");;
        }
        return message.toString();
    }

    private String formatMinorMessage() {
        StringBuilder message = new StringBuilder("Неполадки:\n");
        if (!minorTemperatureSensorsAreOk()) {
            message.append("* отказ температурных датчиков: ");
            message.append(minorTemperatureSensorFailEvents.stream().map(TemperatureSensor::getTemplate)
                .collect(Collectors.joining(", ")));
        }
        if (!streetLightRelayIsOk()) {
            message.append("* отказ реле уличного освещения\n");
        }
        if (!funnelHeatingIsOk()) {
            message.append("* отказ обогрева воронок\n");
        }
        return message.toString();
    }

    private String formatOkMessage() {
        return "Ситуация нормализована";
    }
}

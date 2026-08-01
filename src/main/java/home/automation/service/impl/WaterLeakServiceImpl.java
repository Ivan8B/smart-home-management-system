package home.automation.service.impl;

import home.automation.configuration.WaterLeakConfiguration;
import home.automation.enums.WaterLeakSensor;
import home.automation.enums.WaterLeakStatus;
import home.automation.event.error.WaterLeakErrorEvent;
import home.automation.event.info.WaterLeakDetectedEvent;
import home.automation.exception.ModbusException;
import home.automation.service.BotService;
import home.automation.service.ModbusService;
import home.automation.service.WaterLeakService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class WaterLeakServiceImpl implements WaterLeakService {
    private final Logger logger = LoggerFactory.getLogger(WaterLeakServiceImpl.class);

    /**
     * N4DIG08 (8 Input): 0 = нет входа (контакт разомкнут при NC + low level input)
     */
    private static final int N4DIG08_INPUT_NO_SIGNAL = 0;

    private final WaterLeakConfiguration configuration;
    private final ModbusService modbusService;
    private final BotService botService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ReentrantLock handleLock = new ReentrantLock();

    private volatile WaterLeakStatus status = WaterLeakStatus.OK;
    private volatile Set<WaterLeakSensor> lastTriggeredSensors = EnumSet.noneOf(WaterLeakSensor.class);

    public WaterLeakServiceImpl(
            WaterLeakConfiguration configuration,
            ModbusService modbusService,
            @Lazy BotService botService,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.configuration = configuration;
        this.modbusService = modbusService;
        this.botService = botService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedRateString = "${waterLeak.sensors.controlInterval}")
    private void control() {
        logger.debug("Запущена задача опроса датчиков протечки");

        Set<WaterLeakSensor> triggeredSensors = pollTriggeredSensors();
        if (triggeredSensors == null) {
            status = WaterLeakStatus.ERROR;
            return;
        }

        if (triggeredSensors.isEmpty()) {
            if (status == WaterLeakStatus.LEAK) {
                /* авария сбрасывается только командой */
                logger.info("Протечка не была сброшена вручную, сохраняем аварийный статус");
                return;
            }
            status = WaterLeakStatus.OK;
            return;
        }

        handleLeak(triggeredSensors);
    }

    /**
     * @return множество сработавших датчиков, пустое множество если всё в норме, null при ошибке опроса
     */
    private Set<WaterLeakSensor> pollTriggeredSensors() {
        try {
            int[] values = modbusService.readHoldingRegisters(
                    configuration.getSensorsAddress(),
                    configuration.getSensorsRegisterStart(),
                    WaterLeakSensor.values().length
            );

            if (values.length < WaterLeakSensor.values().length) {
                throw new ModbusException("Опрос датчиков протечки вернул недостаточно регистров");
            }

            Set<WaterLeakSensor> triggered = EnumSet.noneOf(WaterLeakSensor.class);
            for (WaterLeakSensor sensor : WaterLeakSensor.values()) {
                /* Нормально замкнутый контакт: при размыкании (протечка) вход = 0 */
                if (values[sensor.getRegisterOffset()] == N4DIG08_INPUT_NO_SIGNAL) {
                    triggered.add(sensor);
                }
            }
            return triggered;
        } catch (ModbusException e) {
            logger.error("Ошибка опроса датчиков протечки", e);
            applicationEventPublisher.publishEvent(new WaterLeakErrorEvent(this));
            return null;
        }
    }

    private void handleLeak(Set<WaterLeakSensor> triggeredSensors) {
        lastTriggeredSensors = EnumSet.copyOf(triggeredSensors);
        String sensorsText = triggeredSensors.stream()
                .map(WaterLeakSensor::getTemplate)
                .collect(Collectors.joining(", "));
        logger.error("Обнаружена протечка: {}", sensorsText);
        botService.notify("АВАРИЯ: протечка воды (" + sensorsText + ")! Перекрываю краны и отключаю насос");
        applicationEventPublisher.publishEvent(new WaterLeakDetectedEvent(this, triggeredSensors));

        turnPumpOff();
        closeAllValves();

        status = WaterLeakStatus.LEAK;
        botService.notify("Краны перекрыты, насос отключен. Требуется ручной сброс после устранения протечки");
    }

    /**
     * Реле насоса нормально замкнутое: включаем реле, чтобы разомкнуть цепь насоса.
     */
    private void turnPumpOff() {
        try {
            logger.info("Включаем реле отключения насоса (постоянно)");
            modbusService.writeCoil(
                    configuration.getPumpRelayAddress(),
                    configuration.getPumpRelayCoil(),
                    true
            );
        } catch (ModbusException e) {
            logger.error("Ошибка включения реле отключения насоса", e);
            applicationEventPublisher.publishEvent(new WaterLeakErrorEvent(this));
        }
    }

    /**
     * Реле насоса нормально замкнутое: выключаем реле, чтобы замкнуть цепь насоса.
     */
    private void turnPumpOn() {
        try {
            logger.info("Выключаем реле отключения насоса для запуска насоса");
            modbusService.writeCoil(
                    configuration.getPumpRelayAddress(),
                    configuration.getPumpRelayCoil(),
                    false
            );
        } catch (ModbusException e) {
            logger.error("Ошибка выключения реле отключения насоса", e);
            applicationEventPublisher.publishEvent(new WaterLeakErrorEvent(this));
        }
    }

    private void closeAllValves() {
        handleLock.lock();
        try {
            operateValve(
                    configuration.getFilterValvePowerRegister(),
                    configuration.getFilterValveCommandRegister(),
                    false,
                    configuration.getFilterValvePowerDuration(),
                    "сервопривод фильтров"
            );
            operateValve(
                    configuration.getCityValvePowerRegister(),
                    configuration.getCityValveCommandRegister(),
                    false,
                    configuration.getCityValvePowerDuration(),
                    "сервопривод городского ввода"
            );
        } finally {
            handleLock.unlock();
        }
    }

    private void openAllValves() {
        handleLock.lock();
        try {
            operateValve(
                    configuration.getCityValvePowerRegister(),
                    configuration.getCityValveCommandRegister(),
                    true,
                    configuration.getCityValvePowerDuration(),
                    "сервопривод городского ввода"
            );
            operateValve(
                    configuration.getFilterValvePowerRegister(),
                    configuration.getFilterValveCommandRegister(),
                    true,
                    configuration.getFilterValvePowerDuration(),
                    "сервопривод фильтров"
            );
        } finally {
            handleLock.unlock();
        }
    }

    /**
     * Управление сервоприводом: устанавливаем команду направления, подаем питание, ждем, снимаем питание.
     *
     * @param powerCoil     катушка питания сервопривода
     * @param commandCoil   катушка команды сервопривода (0 - закрывается, 1 - открывается)
     * @param open          true - открыть, false - закрыть
     * @param powerDuration время подачи питания на сервопривод
     * @param name          название сервопривода для логирования
     */
    private void operateValve(int powerCoil, int commandCoil, boolean open, Duration powerDuration, String name) {
        String action = open ? "открытия" : "закрытия";
        try {
            logger.info("Устанавливаем команду на {} для {}", action, name);
            modbusService.writeCoil(configuration.getOutputsAddress(), commandCoil, open);
            logger.info("Подаем питание на {} для {}", name, action);
            modbusService.writeCoil(configuration.getOutputsAddress(), powerCoil, true);
            Thread.sleep(powerDuration.toMillis());
            logger.info("Снимаем питание с {}", name);
            modbusService.writeCoil(configuration.getOutputsAddress(), powerCoil, false);
        } catch (InterruptedException e) {
            logger.error("Прервано {} {}", action, name, e);
            applicationEventPublisher.publishEvent(new WaterLeakErrorEvent(this));
        } catch (ModbusException e) {
            logger.error("Ошибка {} {}", action, name, e);
            applicationEventPublisher.publishEvent(new WaterLeakErrorEvent(this));
        }
    }

    @Override
    public void closeFilterValves() {
        handleLock.lock();
        try {
            operateValve(
                    configuration.getFilterValvePowerRegister(),
                    configuration.getFilterValveCommandRegister(),
                    false,
                    configuration.getFilterValvePowerDuration(),
                    "сервопривод фильтров"
            );
        } finally {
            handleLock.unlock();
        }
    }

    @Override
    public void openAllValvesAndTurnOnPump() {
        openAllValves();
        turnPumpOn();
        status = WaterLeakStatus.OK;
    }

    @Override
    public WaterLeakStatus getStatus() {
        return status;
    }

    @Override
    public String getFormattedStatus() {
        if (status == WaterLeakStatus.LEAK && !lastTriggeredSensors.isEmpty()) {
            return status.getTemplate() + " (" +
                    lastTriggeredSensors.stream()
                            .map(WaterLeakSensor::getTemplate)
                            .collect(Collectors.joining(", ")) +
                    ")";
        }
        return status.getTemplate();
    }
}

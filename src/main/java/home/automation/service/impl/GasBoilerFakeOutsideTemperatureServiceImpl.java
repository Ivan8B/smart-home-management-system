package home.automation.service.impl;

import home.automation.configuration.GasBoilerConfiguration;
import home.automation.configuration.GasBoilerFakeOutsideTemperatureConfiguration;
import home.automation.enums.GasBoilerFakeOutsideTemperatureStatus;
import home.automation.enums.TemperatureSensor;
import home.automation.event.error.GasBoilerFakeOutsideTemperatureErrorEvent;
import home.automation.exception.ModbusException;
import home.automation.service.GasBoilerFakeOutsideTemperatureService;
import home.automation.service.ModbusService;
import home.automation.service.TemperatureSensorsService;
import home.automation.utils.decimal.TD_F;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GasBoilerFakeOutsideTemperatureServiceImpl implements GasBoilerFakeOutsideTemperatureService {
    private final Logger logger = LoggerFactory.getLogger(GasBoilerFakeOutsideTemperatureServiceImpl.class);

    private final GasBoilerFakeOutsideTemperatureConfiguration gasBoilerFakeOutsideTemperatureConfiguration;

    private final GasBoilerConfiguration gasBoilerConfiguration;

    private final TemperatureSensorsService temperatureSensorsService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ModbusService modbusService;

    public GasBoilerFakeOutsideTemperatureServiceImpl(
            GasBoilerFakeOutsideTemperatureConfiguration gasBoilerFakeOutsideTemperatureConfiguration,
            GasBoilerConfiguration gasBoilerConfiguration,
            TemperatureSensorsService temperatureSensorsService,
            ApplicationEventPublisher applicationEventPublisher,
            ModbusService modbusService
    ) {
        this.gasBoilerFakeOutsideTemperatureConfiguration = gasBoilerFakeOutsideTemperatureConfiguration;
        this.gasBoilerConfiguration = gasBoilerConfiguration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.modbusService = modbusService;
    }

    @Scheduled(fixedRateString = "${gasBoiler.fakeOutsideTemperature.controlInterval}")
    private void control() {
        logger.debug("Запущена задача управления обманкой температурного датчика газового котла");

        Float currentTemperature =
                temperatureSensorsService.getCurrentTemperatureForSensor(TemperatureSensor.OUTSIDE_TEMPERATURE);
        logger.debug("Температура на улице {}", TD_F.format(currentTemperature));

        if (currentTemperature == null) {
            logger.error("Ошибка получения температуры на улице");
            logger.debug("Отправляем событие по отказу работы с обманкой");
            applicationEventPublisher.publishEvent(new GasBoilerFakeOutsideTemperatureErrorEvent(this));
            return;
        }

        if (gasBoilerConfiguration.getTemperatureWeatherCurveMax() < currentTemperature) {
            logger.debug("Температура на улице выше максимальной по температурной кривой, включаем обманку на +1°");
            turnOn1Degree();
        }
        else {
            logger.debug("Температура на улице ниже максимальной по температурной кривой, выключаем обманку");
            turnOff();
        }
    }

    private void turnOn1Degree() {
        if (getStatus() != GasBoilerFakeOutsideTemperatureStatus.TURNED_ON_1_DEGREE) {
            try {
                modbusService.writeCoil(gasBoilerFakeOutsideTemperatureConfiguration.getMainAddress(),
                        gasBoilerFakeOutsideTemperatureConfiguration.getMainCoil(),
                        true
                );
                modbusService.writeCoil(gasBoilerFakeOutsideTemperatureConfiguration.getSecondaryAddress(),
                        gasBoilerFakeOutsideTemperatureConfiguration.getSecondaryCoil(),
                        false
                );
                logger.info("Обманка газового котла включена на +1°");
            } catch (ModbusException e) {
                logger.error("Ошибка переключения обманки газового котла");
                applicationEventPublisher.publishEvent(new GasBoilerFakeOutsideTemperatureErrorEvent(this));
            }
        } else {
            logger.debug("Обманка газового котла уже включена");
        }
    }

    private void turnOff() {
        if (getStatus() != GasBoilerFakeOutsideTemperatureStatus.TURNED_OFF) {
            try {
                modbusService.writeCoil(gasBoilerFakeOutsideTemperatureConfiguration.getMainAddress(),
                        gasBoilerFakeOutsideTemperatureConfiguration.getMainCoil(),
                        false
                );
                logger.info("Обманка газового котла выключена");
            } catch (ModbusException e) {
                logger.error("Ошибка переключения обманки газового котла");
                applicationEventPublisher.publishEvent(new GasBoilerFakeOutsideTemperatureErrorEvent(this));
            }
        } else {
            logger.debug("Обманка газового котла уже отключена");
        }
    }

    @Override
    public GasBoilerFakeOutsideTemperatureStatus getStatus() {
        try {
            boolean[] pollResult1 =
                    modbusService.readAllCoilsFromZero(gasBoilerFakeOutsideTemperatureConfiguration.getMainAddress());
            boolean[] pollResult2 =
                    modbusService.readAllCoilsFromZero(gasBoilerFakeOutsideTemperatureConfiguration.getMainAddress());
            if (pollResult1.length < 1 || pollResult2.length < 1) {
                throw new ModbusException("Опрос катушек реле обманок вернул пустой результат");
            }
            if (!pollResult1[gasBoilerFakeOutsideTemperatureConfiguration.getMainCoil()]) {
                return GasBoilerFakeOutsideTemperatureStatus.TURNED_OFF;
            }
            else {
                if (pollResult2[gasBoilerFakeOutsideTemperatureConfiguration.getSecondaryCoil()]) {
                    return GasBoilerFakeOutsideTemperatureStatus.TURNED_ON_MINUS_20_DEGREE;
                }
                else {
                    return GasBoilerFakeOutsideTemperatureStatus.TURNED_ON_1_DEGREE;
                }
            }

        } catch (ModbusException e) {
            logger.error("Ошибка получения статуса реле обманки", e);
            applicationEventPublisher.publishEvent(new GasBoilerFakeOutsideTemperatureErrorEvent(this));
            return GasBoilerFakeOutsideTemperatureStatus.ERROR;
        }
    }

    @Override
    public String getFormattedStatus() {
        return getStatus().getTemplate();
    }
}

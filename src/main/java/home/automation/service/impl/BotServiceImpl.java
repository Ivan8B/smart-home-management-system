package home.automation.service.impl;

import home.automation.configuration.TelegramBotConfiguration;
import home.automation.enums.BotCommands;
import home.automation.service.BotService;
import home.automation.service.GasBoilerService;
import home.automation.service.HealthService;
import home.automation.service.StreetLightService;
import home.automation.service.TemperatureSensorsService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Service
public class BotServiceImpl extends TelegramLongPollingBot implements BotService {
    private final Logger logger = LoggerFactory.getLogger(BotServiceImpl.class);

    private final TelegramBotConfiguration telegramBotConfiguration;

    private final TemperatureSensorsService temperatureSensorsService;

    private final GasBoilerService gasBoilerService;

    private final HealthService healthService;

    private final StreetLightService streetLightService;

    public BotServiceImpl(
        TelegramBotConfiguration telegramBotConfiguration,
        TemperatureSensorsService temperatureSensorsService,
        GasBoilerService gasBoilerService,
        @Lazy HealthService healthService,
        StreetLightService streetLightService
    ) {
        super(telegramBotConfiguration.getToken());
        this.telegramBotConfiguration = telegramBotConfiguration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.gasBoilerService = gasBoilerService;
        this.healthService = healthService;
        this.streetLightService = streetLightService;
    }

    @EventListener({ContextRefreshedEvent.class})
    public void init() throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            telegramBotsApi.registerBot(this);
            notify("Система была перезагружена, на связи");
        } catch (TelegramApiException e) {
            logger.error("Ошибка подключения к telegram");
        }
    }

    @Override
    public String getBotUsername() {
        return telegramBotConfiguration.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !userHasPrivileges(update.getMessage().getFrom())) {
            logger.warn("Получено сообщение от неизвестного пользователя {}, игнорируем",
                update.getMessage().getFrom()
            );
            return;
        }

        logger.debug("Получено сообщение {} от пользователя {}",
            update.getMessage().getText(),
            update.getMessage().getFrom()
        );

        String response = processBotCommand(update.getMessage().getText());

        if (response != null) {
            sendMessage(update.getMessage().getChatId(), response);
            logger.debug("Отправлен ответ {} в чат {}", response, update.getMessage().getChatId());
        }
    }

    private @Nullable String processBotCommand(String messageText) {
        if (BotCommands.GET_CURRENT_TEMPERATURES.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на получение температур");
            return temperatureSensorsService.getCurrentTemperaturesFormatted();
        }
        if (BotCommands.GAS_BOILER_ON.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на ручное включение газового котла");
            return gasBoilerService.manualTurnOn();
        }
        if (BotCommands.GAS_BOILER_OFF.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на ручное отключение газового котла");
            return gasBoilerService.manualTurnOff();
        }
        if (BotCommands.GET_GAS_BOILER_STATUS.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на получение статуса отопления");
            return gasBoilerService.getFormattedStatus();
        }
        if (BotCommands.GET_SELF_MONITORING_STATUS.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на получение статуса селф мониторинга");
            return healthService.getFormattedStatus();
        }
        if (BotCommands.GET_STREET_LIGHT_STATUS.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на получение статуса уличного освещения");
            return streetLightService.getFormattedStatus();
        }
        return null;
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void notify(String text) {
        for (Long chatId : telegramBotConfiguration.getChatIds()) {
            sendMessage(chatId, text);
        }
    }

    private boolean userHasPrivileges(User user) {
        return telegramBotConfiguration.getValidUserIds().contains(user.getId());
    }
}

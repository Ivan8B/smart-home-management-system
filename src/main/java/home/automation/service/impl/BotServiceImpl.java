package home.automation.service.impl;

import home.automation.configuration.TelegramBotConfiguration;
import home.automation.enums.BotCommands;
import home.automation.service.BotService;
import home.automation.service.BypassRelayService;
import home.automation.service.FloorHeatingService;
import home.automation.service.FunnelHeatingService;
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

    private final BypassRelayService bypassRelayService;

    private final HealthService healthService;

    private final StreetLightService streetLightService;

    private final FunnelHeatingService funnelHeatingService;

    private final FloorHeatingService floorHeatingService;

    public BotServiceImpl(
        TelegramBotConfiguration telegramBotConfiguration,
        TemperatureSensorsService temperatureSensorsService,
        GasBoilerService gasBoilerService,
        BypassRelayService bypassRelayService, @Lazy HealthService healthService,
        StreetLightService streetLightService,
        FunnelHeatingService funnelHeatingService,
        FloorHeatingService floorHeatingService
    ) {
        super(telegramBotConfiguration.getToken());
        this.telegramBotConfiguration = telegramBotConfiguration;
        this.temperatureSensorsService = temperatureSensorsService;
        this.gasBoilerService = gasBoilerService;
        this.bypassRelayService = bypassRelayService;
        this.healthService = healthService;
        this.streetLightService = streetLightService;
        this.funnelHeatingService = funnelHeatingService;
        this.floorHeatingService = floorHeatingService;
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
        if (BotCommands.GET_STATUS.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на получение статуса системы");
            notify("Считаю статус системы...\n");
            return formatStatus();
        }
        if (BotCommands.GAS_BOILER_ON.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на ручное включение газового котла");
            return gasBoilerService.manualTurnOn();
        }
        if (BotCommands.GAS_BOILER_OFF.getTelegramCommand().equals(messageText)) {
            logger.info("Получена команда на ручное отключение газового котла");
            return gasBoilerService.manualTurnOff();
        }
        return null;
    }

    private String formatStatus() {
        StringBuilder message =
            new StringBuilder("Общий статус системы - ").append(healthService.getFormattedStatus()).append("\n\n");
        message.append("* ").append(gasBoilerService.getFormattedStatus()).append("\n\n");
        message.append("* ").append(temperatureSensorsService.getCurrentTemperaturesFormatted()).append("\n\n");
        message.append("* ").append(bypassRelayService.getFormattedStatus()).append("\n\n");
        message.append("* ").append(streetLightService.getFormattedStatus()).append("\n\n");
        message.append("* ").append(funnelHeatingService.getFormattedStatus()).append("\n\n");
        message.append("* ").append(floorHeatingService.getFormattedStatus()).append("\n\n");
        return message.toString();
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

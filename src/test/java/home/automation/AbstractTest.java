package home.automation;

import home.automation.service.BotService;
import home.automation.service.ModbusService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
        "bot.name = no_data",
        "bot.token = no_data",
        "bot.validUserIds = 0",
        "bot.chatIds = 0",
        "health.controlInterval = PT24H",
        "streetLight.latitude = 55.7522",
        "streetLight.longitude = 37.6156"
    })
@ActiveProfiles("test")
public abstract class AbstractTest {
    @MockBean
    ModbusService modbusService;

    @MockBean
    BotService botService;
}

package home.automation.service;

import home.automation.enums.WaterLeakStatus;

public interface WaterLeakService {
    /**
     * Получить статус системы защиты от протечки
     *
     * @return статус
     */
    WaterLeakStatus getStatus();

    /**
     * Получение статуса текстом
     *
     * @return строка со статусом
     */
    String getFormattedStatus();

    /**
     * Открыть все краны и включить насос (ручное управление, сброс аварийного статуса после протечки)
     */
    void openAllValvesAndTurnOnPump();

    /**
     * Перекрыть только кран фильтров, не трогая скважину и городской ввод (ручное управление, например при отъезде)
     */
    void closeFilterValves();
}

package home.automation.enums;

public enum UniversalSensor {
    MAIN_BEDROOM_AIR
            (
                    "mainBedroom",
                    "в главной спальне"
            ),

    SECOND_BEDROOM_AIR
            (
                    "secondBedroom",
                    "во второй спальне"
            ),

    STUDY_AIR
            (
                    "study",
                    "в кабинете"
            ),

    LOUNGE_AIR
            (
                    "lounge",
                    "в гостиной"
            );

    private final String room;

    private final String template;

    UniversalSensor(String room,
                    String template) {
        this.room = room;
        this.template = template;
    }

    public String getRoom() {
        return room;
    }

    public String getTemplate() {
        return template;
    }
}

package home.automation.utils;

/* Percent Formatter */
public class P_F {
    public static String format(Integer percent) {
        if (percent == null) {
            return "-";
        }
        return percent + "%";
    }

    public static String format(Float percent) {
        if (percent == null) {
            return "-";
        }
        return Math.round(percent) + "%";
    }
}

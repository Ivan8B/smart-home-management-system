package home.automation.utils;

/* PPM Formatter */
public class PPM_F {
    public static String format(Integer co2ppm) {
        if (co2ppm == null) {
            return "-";
        }
        return co2ppm + " ppm";
    }

    public static String format(Float co2ppm) {
        if (co2ppm == null) {
            return "-";
        }
        return Math.round(co2ppm) + " ppm";
    }
}
